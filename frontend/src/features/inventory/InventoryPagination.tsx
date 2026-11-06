import type { PageResult } from './inventoryApi'

export function InventoryPagination({ page, onPage }: { page: PageResult<unknown>; onPage: (value: number) => void }) {
  return <div className="pagination"><span>{page.totalElements} results · Page {page.page + 1} of {Math.max(1, page.totalPages)}</span>
    <div className="pagination-controls"><button className="button secondary" disabled={page.page === 0} onClick={() => onPage(page.page - 1)}>Previous</button>
      <button className="button secondary" disabled={page.page + 1 >= page.totalPages} onClick={() => onPage(page.page + 1)}>Next</button></div></div>
}
