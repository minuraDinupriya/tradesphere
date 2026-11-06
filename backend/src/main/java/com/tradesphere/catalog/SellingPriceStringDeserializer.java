package com.tradesphere.catalog;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Require a JSON string instead of allowing Jackson to coerce a JSON number into the money contract. */
public class SellingPriceStringDeserializer extends ValueDeserializer<String> {
    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            return (String) context.handleUnexpectedToken(String.class, parser);
        }
        return parser.getString();
    }
}
