import { ChangeEvent, FormEvent, useRef, useState } from 'react';

interface SchemaLoaderPanelProps {
  onLoadFromUrl: (baseUrl: string) => Promise<void>;
  onLoadFromFiles: (files: FileList) => Promise<void>;
  loading: boolean;
}

const defaultBase = './output';

export function SchemaLoaderPanel({ onLoadFromUrl, onLoadFromFiles, loading }: SchemaLoaderPanelProps) {
  const [baseUrl, setBaseUrl] = useState<string>(defaultBase);
  const [error, setError] = useState<string | undefined>();
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(undefined);
    try {
      await onLoadFromUrl(baseUrl.trim());
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const handleFolderChange = async (event: ChangeEvent<HTMLInputElement>) => {
    if (event.target.files && event.target.files.length > 0) {
      setError(undefined);
      try {
        await onLoadFromFiles(event.target.files);
      } catch (e) {
        setError((e as Error).message);
      } finally {
        event.target.value = '';
      }
    }
  };

  return (
    <div className="panel loader-panel">
      <div className="flex-between">
        <div>
          <div className="tag neutral">Schema Loader</div>
          <h3 style={{ margin: '6px 0' }}>Load generated JSON descriptors</h3>
          <p className="muted small">
            Point to a folder hosting <code>summary.json</code> + page descriptors or drop a local directory of JSON files.
          </p>
        </div>
        <div className="tag success">React + Vite</div>
      </div>

      <form onSubmit={handleSubmit}>
        <label className="small">Base URL (containing summary.json)</label>
        <div className="input-row">
          <input
            type="text"
            value={baseUrl}
            placeholder="./output"
            onChange={(e) => setBaseUrl(e.target.value)}
            aria-label="Base URL"
          />
          <button type="submit" disabled={loading} aria-label="Load from URL">
            {loading ? 'Loading…' : 'Load'}
          </button>
        </div>
      </form>

      <div className="input-row" style={{ marginTop: 12 }}>
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          disabled={loading}
          aria-label="Load from folder"
        >
          {loading ? 'Reading…' : 'Load folder'}
        </button>
        <input
          ref={fileInputRef}
          type="file"
          multiple
          style={{ display: 'none' }}
          onChange={handleFolderChange}
          aria-label="Folder selector"
          // @ts-expect-error webkitdirectory is supported by Chromium-based browsers
          webkitdirectory="true"
        />
        <span className="muted small">Accepts a directory of JSON outputs from the analyzer.</span>
      </div>

      {error && <div className="alert" role="alert">{error}</div>}
    </div>
  );
}

export default SchemaLoaderPanel;
