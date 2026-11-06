const plannedCapabilities = [
  { title: 'Catalog', description: 'Organize the products your business distributes.' },
  { title: 'Inventory', description: 'Track stock across three warehouses.' },
  { title: 'Orders', description: 'Bring customer orders and warehouse operations together.' },
]

export function DashboardPage() {
  return (
    <>
      <section className="welcome-panel" aria-labelledby="welcome-title">
        <p className="eyebrow">Your business, connected</p>
        <h1 id="welcome-title">Welcome to TradeSphere.</h1>
        <p className="welcome-description">
          A clearer view of commerce and warehouse operations for Lanka Distribution Group.
        </p>
        <div className="notice">
          <strong>A workspace taking shape</strong>
          <p>
            This is a foundation preview. Business features are planned;
            no live products, inventory, or orders are available yet.
          </p>
        </div>
      </section>
      <section aria-labelledby="planned-title">
        <div className="section-heading">
          <h2 id="planned-title">What’s ahead</h2>
          <span>Planned capabilities</span>
        </div>
        <div className="capability-grid">
          {plannedCapabilities.map(({ title, description }) => (
            <article className="capability-card" key={title}>
              <span className="planned-label">Planned</span>
              <h3>{title}</h3>
              <p>{description}</p>
            </article>
          ))}
        </div>
      </section>
    </>
  )
}
