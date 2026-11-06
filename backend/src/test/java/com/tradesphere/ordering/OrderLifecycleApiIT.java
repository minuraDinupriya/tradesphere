package com.tradesphere.ordering;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.tradesphere.catalog.Product;
import com.tradesphere.catalog.ProductService;
import com.tradesphere.catalog.ProductValues;
import com.tradesphere.catalog.SellingPriceService;
import com.tradesphere.inventory.InventoryService;
import com.tradesphere.inventory.StockReceiptRequest;
import com.tradesphere.inventory.Warehouse;
import com.tradesphere.inventory.WarehouseService;
import com.tradesphere.inventory.WarehouseValues;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "tradesphere.bootstrap.enabled=false")
class OrderLifecycleApiIT {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ProductService products;
    @Autowired private SellingPriceService prices;
    @Autowired private CustomerService customers;
    @Autowired private WarehouseService warehouses;
    @Autowired private InventoryService inventory;
    private UUID org;
    private UUID otherOrg;
    private UUID actor;
    private UUID foreignActor;
    private String writer;
    private String viewer;
    private String outsider;
    private String password;
    private Customer customer;
    private Warehouse warehouse;
    private Product first;
    private Product second;
    private String failureName;

    @BeforeEach
    void fixtures() {
        org = UUID.randomUUID(); otherOrg = UUID.randomUUID(); actor = UUID.randomUUID(); foreignActor = UUID.randomUUID();
        writer = "lifecycle-" + UUID.randomUUID(); viewer = "viewer-" + UUID.randomUUID(); outsider = "outside-" + UUID.randomUUID(); password = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO tradesphere.organizations (id,name) VALUES (?,?),(?,?)", org,"Fictional Lifecycle Test",otherOrg,"Other Fictional Lifecycle Test");
        var hash = encoder.encode(password);
        user(actor, writer, org, hash, true);
        user(UUID.randomUUID(), viewer, org, hash, false);
        user(foreignActor, outsider, otherOrg, hash, true);
        customer = customers.create(org, new CustomerValues("Fictional Hardware",null,null,null,null,true));
        warehouse = warehouses.create(org, new WarehouseValues("LIFE-1","Fictional lifecycle warehouse",null,true));
        first = products.create(org, new ProductValues("BOLT-L","Lifecycle bolt",null,"Hardware","Each",true));
        second = products.create(org, new ProductValues("WASH-L","Lifecycle washer",null,"Hardware","Each",true));
        prices.update(org, first.id(), "12.50", 0); prices.update(org, second.id(), "0.10", 0);
        stock(first,10); stock(second,20);
    }

    @AfterEach
    void cleanup() {
        if (failureName != null) {
            jdbc.execute("DROP TRIGGER IF EXISTS " + failureName + " ON tradesphere.order_inventory_movements");
            jdbc.execute("DROP FUNCTION IF EXISTS tradesphere." + failureName + "()");
            jdbc.execute("DROP SEQUENCE IF EXISTS tradesphere." + failureName + "_seq");
        }
        for (var table : new String[]{"order_inventory_movements","order_state_history","stock_reservations","order_items","orders","stock_movements","inventory_balances","warehouses","products","customers","users","organizations"}) {
            jdbc.update("DELETE FROM tradesphere." + table + " WHERE " + (table.equals("organizations") ? "id" : "organization_id") + " IN (?,?)", org, otherOrg);
        }
    }

    @Test
    void allStateAndActionPairsEitherApplyReplayOrRejectWithoutSideEffects() throws Exception {
        stock(first,100); stock(second,100);
        var client = signedIn(writer);
        for (var state : new String[]{"PENDING","CONFIRMED","PROCESSING","FULFILLED","CANCELLED"}) {
            for (var action : new String[]{"confirm","process","cancel","fulfill"}) {
                var id = reach(client,state);
                var target = Map.of("confirm","CONFIRMED","process","PROCESSING","cancel","CANCELLED","fulfill","FULFILLED").get(action);
                var recorded = states(client,id).contains(target);
                var permitted = (state.equals("PENDING") && (action.equals("confirm") || action.equals("cancel")))
                        || (state.equals("CONFIRMED") && (action.equals("process") || action.equals("cancel")))
                        || (state.equals("PROCESSING") && action.equals("fulfill"));
                var before = balances();
                var historyCount = count("order_state_history",id);
                var response = action(client,id,action);
                assertThat(response.statusCode()).as(state + " / " + action + ": " + response.body()).isEqualTo(recorded || permitted ? 200 : 409);
                if (recorded) {
                    assertThat(json(response).path("replayed").asBoolean()).isTrue();
                    assertThat(json(response).path("order").path("status").asString()).isEqualTo(state);
                } else if (permitted) assertThat(json(response).path("order").path("status").asString()).isEqualTo(target);
                if (recorded || !permitted) {
                    assertThat(balances()).isEqualTo(before);
                    assertThat(count("order_state_history",id)).isEqualTo(historyCount);
                }
            }
        }
    }

    @Test
    void pendingCancellationDoesNotTouchStockAndIsRecordedOnce() throws Exception {
        var client = signedIn(writer); var id = pending(client);
        var before = balances();
        assertOk(action(client,id,"cancel"));
        assertThat(json(action(client,id,"cancel")).path("replayed").asBoolean()).isTrue();
        assertThat(balances()).isEqualTo(before);
        assertThat(count("stock_reservations",id)).isZero();
        assertThat(count("order_inventory_movements",id)).isZero();
        assertThat(states(client,id)).containsExactly("PENDING","CANCELLED");
        assertThat(action(client,id,"confirm").statusCode()).isEqualTo(409);
    }

    @Test
    void confirmedCancellationReleasesEachReservationExactlyOnceAndAuditsDeltas() throws Exception {
        var client = signedIn(writer); var id = reach(client,"CONFIRMED");
        assertOk(action(client,id,"cancel"));
        var firstResponse = history(client,id);
        assertThat(firstResponse.path("movements").size()).isEqualTo(2);
        assertBalance(first,10,0,10); assertBalance(second,20,0,20);
        assertThat(reservationStates(id)).containsOnly("RELEASED");
        for (var index=0; index<2; index++) {
            var movement = firstResponse.path("movements").get(index);
            assertThat(movement.path("kind").asString()).isEqualTo("RELEASE");
            assertThat(movement.path("onHandDelta").asLong()).isZero();
            assertThat(movement.path("reservedDelta").asLong()).isEqualTo(-movement.path("quantity").asLong());
            assertThat(movement.path("actorId").asString()).isEqualTo(actor.toString());
        }
        assertThat(json(action(client,id,"cancel")).path("replayed").asBoolean()).isTrue();
        assertThat(history(client,id)).isEqualTo(firstResponse);
    }

    @Test
    void fulfillmentConsumesAllReservationsAndPhysicalStockOnceWithStableAvailability() throws Exception {
        var client = signedIn(writer); var id = reach(client,"PROCESSING");
        assertBalance(first,10,2,8); assertBalance(second,20,3,17);
        assertOk(action(client,id,"fulfill"));
        assertBalance(first,8,0,8); assertBalance(second,17,0,17);
        assertThat(reservationStates(id)).containsOnly("CONSUMED");
        assertThat(states(client,id)).containsExactly("PENDING","CONFIRMED","PROCESSING","FULFILLED");
        var saved = history(client,id);
        for (var index=0; index<2; index++) {
            var movement = saved.path("movements").get(index);
            assertThat(movement.path("kind").asString()).isEqualTo("FULFILL");
            assertThat(movement.path("onHandDelta").asLong()).isEqualTo(-movement.path("quantity").asLong());
            assertThat(movement.path("reservedDelta").asLong()).isEqualTo(-movement.path("quantity").asLong());
        }
        assertThat(json(action(client,id,"fulfill")).path("replayed").asBoolean()).isTrue();
        assertThat(json(action(client,id,"process")).path("replayed").asBoolean()).isTrue();
        assertThat(json(action(client,id,"confirm")).path("replayed").asBoolean()).isTrue();
        assertThat(action(client,id,"cancel").statusCode()).isEqualTo(409);
        assertThat(history(client,id)).isEqualTo(saved);
        assertBalance(first,8,0,8);
    }

    @Test
    void alreadyReservedOrdersCanCloseAfterSourceRecordsAreDeactivated() throws Exception {
        var client = signedIn(writer); var cancelId = reach(client,"CONFIRMED"); var fulfillId = reach(client,"PROCESSING");
        jdbc.update("UPDATE tradesphere.products SET active=false WHERE organization_id=?",org);
        jdbc.update("UPDATE tradesphere.customers SET active=false WHERE organization_id=?",org);
        jdbc.update("UPDATE tradesphere.warehouses SET active=false WHERE organization_id=?",org);
        assertOk(action(client,cancelId,"cancel")); assertOk(action(client,fulfillId,"fulfill"));
        assertBalance(first,8,0,8); assertBalance(second,17,0,17);
    }

    @Test
    void concurrentDuplicateCancellationAndFulfillmentApplyOnce() throws Exception {
        var id = reach(signedIn(writer),"CONFIRMED");
        var a = signedIn(writer); var b = signedIn(writer);
        var cancels = concurrent(() -> action(a,id,"cancel"), () -> action(b,id,"cancel"));
        assertThat(cancels).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(200));
        assertThat(cancels.stream().filter(response -> !json(response).path("replayed").asBoolean()).count()).isEqualTo(1);
        assertThat(count("order_inventory_movements",id)).isEqualTo(2);
        var fulfillId = reach(signedIn(writer),"PROCESSING");
        var fulfills = concurrent(() -> action(a,fulfillId,"fulfill"), () -> action(b,fulfillId,"fulfill"));
        assertThat(fulfills).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(200));
        assertThat(fulfills.stream().filter(response -> !json(response).path("replayed").asBoolean()).count()).isEqualTo(1);
        assertThat(count("order_inventory_movements",fulfillId)).isEqualTo(2);
        assertBalance(first,8,0,8); assertBalance(second,17,0,17);
    }

    @Test
    void cancellationCompetingWithProcessingHasOnePermittedWinner() throws Exception {
        var client = signedIn(writer); var id = reach(client,"CONFIRMED");
        var other = signedIn(writer);
        var responses = concurrent(() -> action(client,id,"process"), () -> action(other,id,"cancel"));
        assertThat(responses.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(200,409);
        var state = status(id);
        if (state.equals("CANCELLED")) { assertBalance(first,10,0,10); assertThat(action(client,id,"fulfill").statusCode()).isEqualTo(409); }
        else { assertThat(state).isEqualTo("PROCESSING"); assertBalance(first,10,2,8); assertOk(action(client,id,"fulfill")); }
    }

    @Test
    void cancellationCannotRacePastProcessingAndFulfillment() throws Exception {
        var client = signedIn(writer); var id = reach(client,"PROCESSING"); var other = signedIn(writer);
        var responses = concurrent(() -> action(client,id,"cancel"), () -> action(other,id,"fulfill"));
        assertThat(responses.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(409,200);
        assertThat(status(id)).isEqualTo("FULFILLED"); assertBalance(first,8,0,8);
        assertThat(count("order_inventory_movements",id)).isEqualTo(2);
    }

    @Test
    void confirmationAndFulfillmentRacesDoNotRepeatReservationOrConsumption() throws Exception {
        var client = signedIn(writer); var id = reach(client,"PROCESSING"); var other = signedIn(writer);
        var responses = concurrent(() -> action(client,id,"confirm"), () -> action(other,id,"fulfill"));
        assertThat(responses).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(200));
        assertThat(status(id)).isEqualTo("FULFILLED"); assertBalance(first,8,0,8);
        assertThat(count("stock_reservations",id)).isEqualTo(2);
        var pendingId = pending(client);
        var cancelRace = concurrent(() -> action(client,pendingId,"confirm"), () -> action(other,pendingId,"cancel"));
        assertThat(cancelRace.get(1).statusCode()).isEqualTo(200);
        assertThat(cancelRace.get(0).statusCode()).isIn(200,409);
        assertThat(status(pendingId)).isEqualTo("CANCELLED"); assertBalance(first,8,0,8);
    }

    @Test
    void overlappingDifferentOrdersAndReceiptsUseConsistentBalanceOrdering() throws Exception {
        var client = signedIn(writer); var cancelId = reach(client,"CONFIRMED"); var fulfillId = reach(client,"PROCESSING");
        var other = signedIn(writer);
        var responses = concurrent(() -> action(client,cancelId,"cancel"), () -> action(other,fulfillId,"fulfill"));
        assertThat(responses).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(200));
        assertBalance(first,8,0,8); assertBalance(second,17,0,17);
        var another = reach(client,"PROCESSING");
        concurrent(() -> { stock(first,5); return client.send("GET","/orders/" + another,null,false); }, () -> action(other,another,"fulfill"));
        assertBalance(first,11,0,11); assertBalance(second,14,0,14);
    }

    @Test
    void databaseFailureOnLaterMovementRollsBackBothCancellationAndFulfillment() throws Exception {
        var client = signedIn(writer);
        for (var operation : new String[]{"cancel","fulfill"}) {
            var id = reach(client,operation.equals("cancel") ? "CONFIRMED" : "PROCESSING");
            var before = balances();
            installFailure();
            assertThat(action(client,id,operation).statusCode()).isEqualTo(500);
            assertThat(jdbc.queryForObject("SELECT last_value FROM tradesphere." + failureName + "_seq",Long.class)).isEqualTo(2);
            assertThat(balances()).isEqualTo(before);
            assertThat(reservationStates(id)).containsOnly("ACTIVE");
            assertThat(count("order_inventory_movements",id)).isZero();
            assertThat(states(client,id)).doesNotContain(operation.equals("cancel") ? "CANCELLED" : "FULFILLED");
            removeFailure();
            assertOk(action(client,id,operation));
        }
    }

    @Test
    void inconsistentReservationCannotPartiallyReleaseOrConsumeOtherItems() throws Exception {
        var client = signedIn(writer); var id = reach(client,"CONFIRMED");
        jdbc.update("UPDATE tradesphere.stock_reservations SET quantity=quantity+1 WHERE organization_id=? AND order_id=?::uuid AND product_id=?",org,id,first.id());
        var before = balances();
        assertThat(action(client,id,"cancel").statusCode()).isEqualTo(409);
        assertThat(balances()).isEqualTo(before); assertThat(count("order_inventory_movements",id)).isZero();
        assertThat(status(id)).isEqualTo("CONFIRMED");
    }

    @Test
    void allActionsAndHistoryRespectPermissionsTenantIsolationAndCsrf() throws Exception {
        var client = signedIn(writer); var id = reach(client,"CONFIRMED");
        var readOnly = signedIn(viewer); var foreign = signedIn(outsider);
        for (var action : new String[]{"process","cancel","fulfill"}) {
            assertThat(action(readOnly,id,action).statusCode()).isEqualTo(403);
            assertThat(action(foreign,id,action).statusCode()).isEqualTo(404);
            assertThat(client.send("POST","/orders/" + id + "/" + action,null,false).statusCode()).isEqualTo(403);
            assertThat(action(new ApiClient(),id,action).statusCode()).isIn(401,403);
        }
        assertThat(readOnly.send("GET","/orders/" + id + "/history",null,false).statusCode()).isEqualTo(200);
        assertThat(foreign.send("GET","/orders/" + id + "/history",null,false).statusCode()).isEqualTo(404);
        assertThat(new ApiClient().send("GET","/orders/" + id + "/history",null,false).statusCode()).isEqualTo(401);
        assertThat(status(id)).isEqualTo("CONFIRMED"); assertBalance(first,10,2,8);
    }

    @Test
    void databaseConstraintsEnforceAuditOwnershipClosureAndOperationShape() throws Exception {
        var client = signedIn(writer); var id = reach(client,"CONFIRMED");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO tradesphere.order_state_history (id,organization_id,order_id,to_status,actor_id) VALUES (?,?,?::uuid,'PROCESSING',?)",UUID.randomUUID(),org,id,actor))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_order_history_transition");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO tradesphere.order_state_history (id,organization_id,order_id,from_status,to_status,actor_id) VALUES (?,?,?::uuid,'CONFIRMED','PROCESSING',?)",UUID.randomUUID(),org,id,foreignActor))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_order_history_actor");
        assertThatThrownBy(() -> jdbc.update("UPDATE tradesphere.stock_reservations SET status='RELEASED' WHERE organization_id=? AND order_id=?::uuid",org,id))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_reservations_closure");
        assertOk(action(client,id,"cancel"));
        assertThatThrownBy(() -> jdbc.update("UPDATE tradesphere.order_inventory_movements SET on_hand_delta=-1 WHERE organization_id=? AND order_id=?::uuid",org,id))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_order_movement_deltas");
        assertThatThrownBy(() -> jdbc.update("UPDATE tradesphere.order_inventory_movements SET actor_id=? WHERE organization_id=? AND order_id=?::uuid",foreignActor,org,id))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_order_movement_actor");
    }

    private void installFailure() {
        failureName = "life_fail_" + UUID.randomUUID().toString().replace("-", "");
        var last = List.of(first.id(),second.id()).stream().map(UUID::toString).sorted().toList().get(1);
        jdbc.execute("CREATE SEQUENCE tradesphere." + failureName + "_seq");
        jdbc.execute("CREATE FUNCTION tradesphere." + failureName + "() RETURNS trigger LANGUAGE plpgsql AS $test$ BEGIN IF NEW.organization_id='" + org + "'::uuid THEN PERFORM nextval('tradesphere." + failureName + "_seq'); IF NEW.product_id='" + last + "'::uuid THEN RAISE EXCEPTION 'Simulated lifecycle movement failure'; END IF; END IF; RETURN NEW; END; $test$");
        jdbc.execute("CREATE TRIGGER " + failureName + " BEFORE INSERT ON tradesphere.order_inventory_movements FOR EACH ROW EXECUTE FUNCTION tradesphere." + failureName + "()");
    }
    private void removeFailure() {
        jdbc.execute("DROP TRIGGER " + failureName + " ON tradesphere.order_inventory_movements");
        jdbc.execute("DROP FUNCTION tradesphere." + failureName + "()"); jdbc.execute("DROP SEQUENCE tradesphere." + failureName + "_seq"); failureName=null;
    }
    private String pending(ApiClient client) throws Exception {
        var response=client.send("POST","/orders",Map.of("requestId",UUID.randomUUID(),"customerId",customer.id(),"warehouseId",warehouse.id(),"items",List.of(Map.of("productId",first.id(),"quantity",2),Map.of("productId",second.id(),"quantity",3))),true);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201); return json(response).path("order").path("id").asString();
    }
    private String reach(ApiClient client,String state) throws Exception {
        var id=pending(client);
        if (List.of("CONFIRMED","PROCESSING","FULFILLED").contains(state)) assertOk(action(client,id,"confirm"));
        if (List.of("PROCESSING","FULFILLED").contains(state)) assertOk(action(client,id,"process"));
        if (state.equals("FULFILLED")) assertOk(action(client,id,"fulfill"));
        if (state.equals("CANCELLED")) assertOk(action(client,id,"cancel"));
        return id;
    }
    private List<String> states(ApiClient client,String id) throws Exception {
        var result=new ArrayList<String>(); var transitions=history(client,id).path("transitions");
        for (int index=0; index<transitions.size(); index++) result.add(transitions.get(index).path("toStatus").asString());
        return result;
    }
    private JsonNode history(ApiClient client,String id) throws Exception { var response=client.send("GET","/orders/"+id+"/history",null,false); assertThat(response.statusCode()).isEqualTo(200); return json(response); }
    private HttpResponse<String> action(ApiClient client,String id,String action) throws Exception { return client.send("POST","/orders/"+id+"/"+action,null,true); }
    private static void assertOk(HttpResponse<String> response) { assertThat(response.statusCode()).as(response.body()).isEqualTo(200); }
    private static JsonNode json(HttpResponse<String> response) { return JSON.readTree(response.body()); }
    private long count(String table,String id) { return jdbc.queryForObject("SELECT count(*) FROM tradesphere."+table+" WHERE organization_id=? AND order_id=?::uuid",Long.class,org,id); }
    private String status(String id) { return jdbc.queryForObject("SELECT status FROM tradesphere.orders WHERE organization_id=? AND id=?::uuid",String.class,org,id); }
    private List<String> reservationStates(String id) { return jdbc.queryForList("SELECT status FROM tradesphere.stock_reservations WHERE organization_id=? AND order_id=?::uuid",String.class,org,id); }
    private List<Map<String,Object>> balances() { return jdbc.queryForList("SELECT product_id,on_hand,reserved,available FROM tradesphere.inventory_balances WHERE organization_id=? ORDER BY product_id",org); }
    private void assertBalance(Product product,long onHand,long reserved,long available) {
        var row=jdbc.queryForMap("SELECT on_hand,reserved,available FROM tradesphere.inventory_balances WHERE organization_id=? AND product_id=?",org,product.id());
        assertThat(((Number)row.get("on_hand")).longValue()).isEqualTo(onHand); assertThat(((Number)row.get("reserved")).longValue()).isEqualTo(reserved); assertThat(((Number)row.get("available")).longValue()).isEqualTo(available);
    }
    private void stock(Product product,long quantity) { inventory.receive(org,actor,new StockReceiptRequest(UUID.randomUUID(),warehouse.id(),product.id(),quantity,"Fictional lifecycle test",null)); }
    private void user(UUID id,String name,UUID organization,String hash,boolean orderWrite) { jdbc.update("INSERT INTO tradesphere.users (id,organization_id,username,password_hash,can_write_orders,can_write_catalog,can_write_inventory) VALUES (?,?,?,?,?,true,true)",id,organization,name,hash,orderWrite); }
    private ApiClient signedIn(String username) throws Exception { var client=new ApiClient(); client.login(username); return client; }
    private List<HttpResponse<String>> concurrent(Callable<HttpResponse<String>> left,Callable<HttpResponse<String>> right) throws Exception {
        var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        try (var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks=List.of(left,right).stream().map(action -> executor.submit(() -> { ready.countDown(); if(!start.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out"); return action.call(); })).toList();
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); start.countDown(); var responses=new ArrayList<HttpResponse<String>>();
            for(var task:tasks) responses.add(task.get(20,TimeUnit.SECONDS)); return responses;
        }
    }
    private class ApiClient {
        private final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        private String csrf; private String header;
        void token() throws Exception { var body=json(send("GET","/auth/csrf",null,false)); csrf=body.path("token").asString(); header=body.path("headerName").asString(); }
        void login(String username) throws Exception {
            token(); var body="username="+URLEncoder.encode(username,StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(password,StandardCharsets.UTF_8);
            var response=http.send(HttpRequest.newBuilder(uri("/auth/login")).header(header,csrf).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(204); token();
        }
        HttpResponse<String> send(String method,String path,Object body,boolean secure) throws Exception {
            var request=HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15)); if(secure && csrf!=null) request.header(header,csrf); if(body!=null) request.header("Content-Type","application/json");
            request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
        }
        private URI uri(String path) { return URI.create("http://127.0.0.1:"+port+"/api/v1"+path); }
    }
}
