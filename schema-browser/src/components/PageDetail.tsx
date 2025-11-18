import { FieldDescriptor, FormDescriptor, OutputSectionDescriptor, PageSchema } from '../types';

interface PageDetailProps {
  page?: PageSchema;
}

function FieldPill({ field }: { field: FieldDescriptor }) {
  return (
    <div className="field">
      <strong>{field.name ?? 'unnamed'}</strong>
      {field.label && <div className="muted small">{field.label}</div>}
      <div className="small">
        {field.type ?? 'field'}
        {field.required ? ' · required' : ''}
        {field.maxLength ? ` · max ${field.maxLength}` : ''}
        {field.min !== undefined || field.max !== undefined ? ` · range ${field.min ?? '-'}–${field.max ?? '-'}` : ''}
      </div>
      {field.placeholder && <div className="muted small">placeholder: {field.placeholder}</div>}
      {field.constraints && field.constraints.length > 0 && (
        <div className="muted small">constraints: {field.constraints.join(', ')}</div>
      )}
      {field.bindingExpressions && field.bindingExpressions.length > 0 && (
        <div className="muted small">bindings: {field.bindingExpressions.join(', ')}</div>
      )}
      {field.options && field.options.length > 0 && (
        <div className="muted small">
          options: {field.options.map((o) => o.label ?? o.value ?? '').filter(Boolean).join(', ')}
        </div>
      )}
    </div>
  );
}

function FormCard({ form }: { form: FormDescriptor }) {
  return (
    <div className="card">
      <h4>{form.formId ?? form.action ?? 'Form'}</h4>
      <div className="muted small">
        {form.method ?? 'GET'} → {form.action ?? '(action unknown)'}
      </div>
      {form.backingBeanClassName && <div className="small">Backed by: {form.backingBeanClassName}</div>}
      {form.fields && form.fields.length > 0 ? (
        <div className="field-grid" style={{ marginTop: 10 }}>
          {form.fields.map((field, idx) => (
            <FieldPill field={field} key={`${field.name ?? 'field'}-${idx}`} />
          ))}
        </div>
      ) : (
        <div className="muted small" style={{ marginTop: 8 }}>
          No fields detected for this form.
        </div>
      )}
    </div>
  );
}

function OutputCard({ section }: { section: OutputSectionDescriptor }) {
  return (
    <div className="card">
      <h4>{section.sectionId ?? section.type ?? 'Output'}</h4>
      <div className="muted small">
        {section.type ?? 'Unknown'}
        {section.itemVariable ? ` · item var: ${section.itemVariable}` : ''}
      </div>
      {section.itemsExpression && <div className="small">Items: {section.itemsExpression}</div>}
      {section.fields && section.fields.length > 0 ? (
        <table className="table-fields" style={{ marginTop: 10 }}>
          <thead>
            <tr>
              <th>Name</th>
              <th>Label</th>
              <th>Binding</th>
              <th>Notes</th>
            </tr>
          </thead>
          <tbody>
            {section.fields.map((f, idx) => (
              <tr key={`${f.name ?? f.label ?? 'field'}-${idx}`}>
                <td>{f.name ?? '—'}</td>
                <td>{f.label ?? '—'}</td>
                <td>{f.bindingExpression ?? f.rawText ?? '—'}</td>
                <td className="muted small">{f.notes?.join(', ') ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <div className="muted small" style={{ marginTop: 8 }}>
          No output fields detected.
        </div>
      )}
    </div>
  );
}

export function PageDetail({ page }: PageDetailProps) {
  if (!page) {
    return <div className="muted">Select a page to view its schema.</div>;
  }

  return (
    <div className="panel">
      <div className="flex-between">
        <div>
          <div className="tag neutral">Page</div>
          <h3 style={{ margin: '6px 0' }}>{page.pageId ?? 'Unknown page'}</h3>
          {page.title && <div className="muted">{page.title}</div>}
        </div>
        {page.metadata?.confidence && (
          <div className={`tag ${page.metadata.confidence.toUpperCase() === 'HIGH' ? 'success' : 'neutral'}`}>
            Confidence: {page.metadata.confidence}
          </div>
        )}
      </div>

      {page.metadata && (
        <div className="metadata section">
          <div className="card">
            <h4>Controllers</h4>
            <div className="muted small">
              {(page.metadata.controllerCandidates ?? ['n/a']).join(', ')}
            </div>
          </div>
          <div className="card">
            <h4>Backing beans</h4>
            <div className="muted small">
              {(page.metadata.backingBeanCandidates ?? ['n/a']).join(', ')}
            </div>
          </div>
          <div className="card">
            <h4>Notes</h4>
            <div className="muted small">
              {(page.metadata.notes ?? ['No notes captured']).join(' · ')}
            </div>
          </div>
        </div>
      )}

      <div className="section">
        <h3>Forms</h3>
        {page.forms && page.forms.length > 0 ? (
          <div className="cards">
            {page.forms.map((form, idx) => (
              <FormCard form={form} key={`${form.formId ?? form.action ?? 'form'}-${idx}`} />
            ))}
          </div>
        ) : (
          <div className="muted">No forms discovered.</div>
        )}
      </div>

      <div className="section">
        <h3>Outputs</h3>
        {page.outputs && page.outputs.length > 0 ? (
          <div className="cards">
            {page.outputs.map((section, idx) => (
              <OutputCard section={section} key={`${section.sectionId ?? section.type ?? 'output'}-${idx}`} />
            ))}
          </div>
        ) : (
          <div className="muted">No outputs detected.</div>
        )}
      </div>
    </div>
  );
}

export default PageDetail;
