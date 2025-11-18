import { useCallback, useMemo, useState } from 'react';
import SchemaLoaderPanel from './components/SchemaLoaderPanel';
import SchemaList from './components/SchemaList';
import PageDetail from './components/PageDetail';
import { PageSchema, SchemaSummary, SummaryPageEntry } from './types';

function joinPaths(base: string, relative: string): string {
  const sanitizedBase = base.replace(/\/+$/, '');
  const sanitizedRelative = relative.replace(/^\/+/, '');
  return `${sanitizedBase}/${sanitizedRelative}`;
}

async function readJsonFile(file: File) {
  const text = await file.text();
  return JSON.parse(text);
}

export default function App() {
  const [summary, setSummary] = useState<SchemaSummary | undefined>();
  const [pages, setPages] = useState<PageSchema[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>();
  const [selectedPageId, setSelectedPageId] = useState<string | undefined>();

  const summaryPages: SummaryPageEntry[] = useMemo(() => {
    if (summary?.pages && summary.pages.length > 0) {
      return summary.pages;
    }
    return pages.map((page) => ({ pageId: page.pageId, output: page.pageId }));
  }, [summary, pages]);

  const fetchSummaryAndPages = useCallback(
    async (baseUrl: string) => {
      setLoading(true);
      setError(undefined);
      try {
        const summaryUrl = baseUrl.endsWith('.json') ? baseUrl : joinPaths(baseUrl, 'summary.json');
        const summaryResponse = await fetch(summaryUrl);
        if (!summaryResponse.ok) {
          throw new Error(`Unable to load summary at ${summaryUrl}`);
        }
        const summaryJson: SchemaSummary = await summaryResponse.json();
        const fetchedPages: PageSchema[] = [];
        if (summaryJson.pages && summaryJson.pages.length > 0) {
          for (const page of summaryJson.pages) {
            if (!page.output && !page.pageId) continue;
            const outputPath = page.output ?? `${page.pageId}.json`;
            const pageUrl = joinPaths(baseUrl, outputPath);
            const pageResponse = await fetch(pageUrl);
            if (!pageResponse.ok) {
              // Continue loading other pages but note the error
              console.warn(`Failed to load ${pageUrl}`);
              continue;
            }
            const pageJson: PageSchema = await pageResponse.json();
            fetchedPages.push(pageJson);
          }
        }
        setSummary(summaryJson);
        setPages(fetchedPages);
        setSelectedPageId(fetchedPages[0]?.pageId);
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  const loadFromFiles = useCallback(async (files: FileList) => {
    setLoading(true);
    setError(undefined);
    try {
      const allFiles = Array.from(files);
      const summaryFile = allFiles.find((file) => file.name.toLowerCase() === 'summary.json');
      let summaryJson: SchemaSummary | undefined;
      let discoveredPages: PageSchema[] = [];

      if (summaryFile) {
        summaryJson = await readJsonFile(summaryFile);
        const fileMap = new Map<string, File>();
        allFiles.forEach((f) => fileMap.set(f.webkitRelativePath || f.name, f));
        if (summaryJson.pages) {
          for (const entry of summaryJson.pages) {
            const output = entry.output ?? `${entry.pageId}.json`;
            if (!output) continue;
            const matchingFile = Array.from(fileMap.values()).find((file) =>
              file.name === output || file.webkitRelativePath.endsWith(output),
            );
            if (matchingFile) {
              const json = await readJsonFile(matchingFile);
              discoveredPages.push(json);
            }
          }
        }
      }

      if (!summaryJson) {
        const pagePromises = allFiles
          .filter((file) => file.name.toLowerCase().endsWith('.json') && file !== summaryFile)
          .map((file) => readJsonFile(file) as Promise<PageSchema>);
        discoveredPages = await Promise.all(pagePromises);
      }

      setSummary(
        summaryJson ?? { pageCount: discoveredPages.length, pages: discoveredPages.map((p) => ({ pageId: p.pageId })) },
      );
      setPages(discoveredPages);
      setSelectedPageId(discoveredPages[0]?.pageId);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  const handleLoadFromUrl = useCallback(
    async (baseUrl: string) => {
      try {
        await fetchSummaryAndPages(baseUrl);
      } catch (e) {
        setError((e as Error).message);
      }
    },
    [fetchSummaryAndPages],
  );

  const selectedPage = useMemo(() => pages.find((p) => p.pageId === selectedPageId), [pages, selectedPageId]);

  return (
    <div className="app-shell">
      <header className="header">
        <div>
          <h1>LIDE Schema Browser</h1>
          <div className="subtitle">Inspect generated JSP JSON contracts in seconds.</div>
        </div>
        <div className="tag neutral">P9 UI preview</div>
      </header>
      <div className="sidebar">
        <SchemaLoaderPanel onLoadFromUrl={handleLoadFromUrl} onLoadFromFiles={loadFromFiles} loading={loading} />
        <SchemaList pages={summaryPages} selectedPageId={selectedPageId} onSelect={setSelectedPageId} />
      </div>
      <main className="content">
        {error && <div className="alert">{error}</div>}
        {loading && <div className="loader">Loading schemas…</div>}
        {!loading && <PageDetail page={selectedPage} />}
      </main>
    </div>
  );
}
