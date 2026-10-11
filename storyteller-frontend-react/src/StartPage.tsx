import { useState } from 'react';
import type { FormEvent } from 'react';

interface Props {
  busy: boolean;
  onCreate: (title: string) => void;
  onResume: (id: string) => void;
  onImport: (file: File) => void;
}

export default function StartPage({ busy, onCreate, onResume, onImport }: Props) {
  const [title, setTitle] = useState('');
  const [sessionId, setSessionId] = useState('');
  const [bundle, setBundle] = useState<File | null>(null);
  function importBundle(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (bundle) onImport(bundle);
  }
  return <main className="start-page">
    <section className="panel start-panel">
      <p className="eyebrow">Local AI storytelling</p>
      <h1>Storyteller</h1>
      <p className="intro">Create a session and start writing with the model configured on this server.</p>
      <form className="stack" onSubmit={event => { event.preventDefault(); onCreate(title.trim()); }}>
        <label htmlFor="title">Story title <span>(optional)</span></label>
        <input id="title" maxLength={255} autoComplete="off" autoFocus value={title}
          onChange={event => setTitle(event.target.value)} disabled={busy} />
        <button disabled={busy}>Start a story</button>
      </form>
      <div className="start-divider"><span>or</span></div>
      <form className="stack" onSubmit={event => { event.preventDefault(); onResume(sessionId.trim()); }}>
        <label htmlFor="sessionId">Resume an infinite session <span>(session ID)</span></label>
        <input id="sessionId" maxLength={36} autoComplete="off" required value={sessionId}
          onChange={event => setSessionId(event.target.value)} disabled={busy} />
        <button className="secondary-action" disabled={busy || !sessionId.trim()}>Resume session</button>
      </form>
      <div className="start-divider"><span>or</span></div>
      <form className="stack import-form" onSubmit={importBundle}>
        <label htmlFor="bundle">Import CLI session <span>(ZIP)</span></label>
        <input id="bundle" type="file" accept=".zip,application/zip" required disabled={busy}
          onChange={event => setBundle(event.target.files?.[0] ?? null)} />
        <button className="secondary-action" disabled={busy || !bundle}>Import session</button>
      </form>
    </section>
  </main>;
}
