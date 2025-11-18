import { SummaryPageEntry } from '../types';

interface SchemaListProps {
  pages: SummaryPageEntry[];
  selectedPageId?: string;
  onSelect: (pageId: string) => void;
}

function confidenceTag(confidence?: string) {
  if (!confidence) return 'neutral';
  if (confidence.toUpperCase() === 'HIGH') return 'success';
  if (confidence.toUpperCase() === 'LOW') return 'warning';
  return 'neutral';
}

export function SchemaList({ pages, selectedPageId, onSelect }: SchemaListProps) {
  return (
    <div className="panel">
      <h3>Pages</h3>
      <ul className="page-list">
        {pages.map((page) => (
          <li
            key={page.pageId}
            className={`page-item ${selectedPageId === page.pageId ? 'active' : ''}`}
            onClick={() => page.pageId && onSelect(page.pageId)}
          >
            <div className="flex-between">
              <div>
                <strong>{page.pageId}</strong>
                <div className="muted small">
                  {page.forms ?? 0} forms · {page.fields ?? 0} fields · {page.outputs ?? 0} outputs ·{' '}
                  {page.navigationTargets ?? 0} nav · {page.urlParameters ?? 0} params
                </div>
              </div>
              <div className={`tag ${confidenceTag(page.confidence)}`}>
                {page.confidence ?? 'UNKNOWN'}
              </div>
            </div>
            {page.confidenceScore !== undefined && (
              <div className="muted small">Confidence score: {page.confidenceScore.toFixed(2)}</div>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}

export default SchemaList;
