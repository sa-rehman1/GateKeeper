import React, { useEffect, useState, type FormEvent } from 'react';
import { createRoot } from 'react-dom/client';
import { Activity, Building2, Clock3, GitBranch, KeyRound, LogOut, Pencil, Plus, RefreshCw, Route as RouteIcon, ShieldCheck, Trash2, X } from 'lucide-react';
import './style.css';

type Role = 'PLATFORM_ADMIN' | 'TENANT_ADMIN' | 'GATEWAY_SERVICE';
type Status = 'ACTIVE' | 'PENDING' | 'SUSPENDED' | 'DELETED';
type Login = { token: string; tokenType: string; expiresInSeconds: number; role: Role; organizationId: number | null };
type Session = Login & { username: string; expiresAt: number };
type Organization = { id: number; name: string; slug: string; status: Status; createdAt: string };
type TenantAdmin = { id: number; username: string; enabled: boolean };
type HeaderRule = { direction: string; action: string; headerName: string; headerValue: string | null };
type RouteData = { id: number; routeId: string; name: string; pathPattern: string; targetBaseUrl: string; targetPathPrefix: string; requestsPerMinute: number | null; responseTimeoutMs: number; idempotencyEnabled: boolean; active: boolean; configurationVersion: number; allowedMethods: string[]; headerRules: HeaderRule[] };
type Version = { id: number; versionNumber: number; changeType: string; configurationSnapshot: string; createdAt: string };
type Audit = { id: number; actor: string; action: string; resourceId: string; resourceType: string; result: string; durationMs: number; createdAt: string; details: string };
type Page = 'overview' | 'organizations' | 'routes' | 'history' | 'audit' | 'profile';
type FormState = { kind: 'organization' | 'admin' | 'route' | null; id?: number; organization?: Organization; route?: RouteData };
const API = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081').replace(/\/$/, '');
const SESSION_KEY = 'gatekeeper.session';
function getSession(): Session | null { try { const value = JSON.parse(sessionStorage.getItem(SESSION_KEY) || 'null') as Session | null; return value && value.expiresAt > Date.now() ? value : null; } catch { return null; } }
function api<T>(path: string, session: Session | null, method = 'GET', body?: unknown): Promise<T> {
  return fetch(API + path, { method, headers: { 'Content-Type': 'application/json', ...(session ? { Authorization: 'Bearer ' + session.token } : {}) }, body: body === undefined ? undefined : JSON.stringify(body) })
    .then(async response => { const text = await response.text(); let data: unknown; try { data = text ? JSON.parse(text) : null; } catch { data = text; }
      if (!response.ok) { const message = data && typeof data === 'object' && 'message' in data ? String(data.message) : 'HTTP ' + response.status; throw new Error(message); }
      return data as T; }).catch(error => { if (error instanceof TypeError) throw new Error('Cannot reach API Management Service on port 8081.'); throw error; });
}
const methods = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'];
const baseRoute = (): Omit<RouteData, 'id' | 'configurationVersion'> => ({ routeId: '', name: '', pathPattern: '', targetBaseUrl: '', targetPathPrefix: '/', requestsPerMinute: null, responseTimeoutMs: 5000, idempotencyEnabled: false, active: false, allowedMethods: ['GET'], headerRules: [] });
function formatDate(value?: string) { return value ? new Date(value).toLocaleString() : '—'; }
function App() {
  const [session, setSession] = useState<Session | null>(getSession);
  const [username, setUsername] = useState(''); const [password, setPassword] = useState('');
  const [page, setPage] = useState<Page>('overview'); const [loading, setLoading] = useState(false);
  const [error, setError] = useState(''); const [notice, setNotice] = useState('');
  const [organizations, setOrganizations] = useState<Organization[]>([]); const [routes, setRoutes] = useState<RouteData[]>([]);
  const [admin, setAdmin] = useState<TenantAdmin | null>(null); const [audit, setAudit] = useState<Audit[]>([]);
  const [profile, setProfile] = useState<Organization | null>(null); const [versions, setVersions] = useState<Version[]>([]);
  const [historyName, setHistoryName] = useState(''); const [form, setForm] = useState<FormState>({ kind: null });
  const [search, setSearch] = useState('');
  const logout = () => { sessionStorage.removeItem(SESSION_KEY); setSession(null); setPage('overview'); };
  useEffect(() => { if (!session) return; const delay = session.expiresAt - Date.now(); if (delay <= 0) { logout(); return; } const timer = setTimeout(logout, delay); return () => clearTimeout(timer); }, [session]);
  async function load() {
    if (!session) return; setLoading(true); setError('');
    try {
      if (session.role === 'PLATFORM_ADMIN') { const data = await api<Organization[]>('/api/platform/organizations', session); setOrganizations(data); }
      else if (session.role === 'TENANT_ADMIN') {
        const [rs, org, events] = await Promise.all([api<RouteData[]>('/api/tenant/routes', session), api<Organization>('/api/tenant/organization', session), api<Audit[]>('/api/tenant/audit-records', session)]);
        setRoutes(rs); setProfile(org); setAudit(events);
      }
    } catch (err) { setError((err as Error).message); } finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, [session]);
  async function signIn(e: FormEvent) { e.preventDefault(); setLoading(true); setError('');
    try { const result = await api<Login>('/api/auth/login', null, 'POST', { username: username.trim(), password });
      if (result.role === 'GATEWAY_SERVICE') throw new Error('Service accounts cannot use the management console.');
      const next = { ...result, username: username.trim(), expiresAt: Date.now() + result.expiresInSeconds * 1000 };
      sessionStorage.setItem(SESSION_KEY, JSON.stringify(next)); setSession(next); setPassword('');
    } catch (err) { setError((err as Error).message); } finally { setLoading(false); }
  }
  async function run(task: () => Promise<unknown>, success: string) { setError(''); try { await task(); setNotice(success); setForm({ kind: null }); await load(); } catch (err) { setError((err as Error).message); } }
  async function removeOrganization(org: Organization) { if (!confirm('Delete organization ' + org.name + '?')) return; await run(() => api('/api/platform/organizations/' + org.id, session, 'DELETE'), 'Organization deleted'); }
  async function changeStatus(org: Organization, value: Status) { await run(() => api('/api/platform/organizations/' + org.id + '/status', session, 'PATCH', { status: value }), 'Organization status updated'); }
  async function changeRouteStatus(route: RouteData) { await run(() => api('/api/tenant/routes/' + route.id + '/status', session, 'PATCH', { active: !route.active }), 'Route status updated'); }
  async function removeRoute(route: RouteData) { if (!confirm('Delete route ' + route.name + '?')) return; await run(() => api('/api/tenant/routes/' + route.id, session, 'DELETE'), 'Route deleted'); }
  async function showAdmin(org: Organization) { setError(''); try { const a = await api<TenantAdmin>('/api/platform/organizations/' + org.id + '/tenant-admin', session); setAdmin(a); }
    catch { setAdmin(null); } setForm({ kind: 'admin', organization: org }); }
  async function removeAdmin(org: Organization) { if (!confirm('Remove tenant admin?')) return; await run(() => api('/api/platform/organizations/' + org.id + '/tenant-admin', session, 'DELETE'), 'Tenant admin removed'); }
  async function showHistory(route: RouteData) { setError(''); setHistoryName(route.routeId); setPage('history');
    try { setVersions(await api<Version[]>('/api/tenant/route-versions/' + encodeURIComponent(route.routeId), session)); }
    catch (err) { setError((err as Error).message); } }
  const isPlatform = session?.role === 'PLATFORM_ADMIN';
  const pages: { id: Page; title: string; icon: React.ComponentType<{ size?: number }> }[] = [
    { id: 'overview', title: 'Overview', icon: ShieldCheck },
    ...(isPlatform ? [{ id: 'organizations' as Page, title: 'Organizations', icon: Building2 }] : [
      { id: 'routes' as Page, title: 'API Routes', icon: RouteIcon }, { id: 'audit' as Page, title: 'Audit Logs', icon: Activity }, { id: 'profile' as Page, title: 'Organization', icon: Building2 }
    ])
  ];
  if (!session) return <div className="login"><section className="login-story"><div className="brand"><ShieldCheck/> gatekeeper<span>.</span></div>
    <div><div className="eyebrow">THE API CONTROL PLANE</div><h1>Every route.<br/><em>One clear view.</em></h1><p>Configure, secure, and manage your API infrastructure in one place.</p>
    <div className="login-graphic"><RouteIcon size={30}/><span>ROUTING</span><GitBranch size={27}/><span>VERSIONING</span><Activity size={27}/><span>AUDIT</span></div></div><small>GATEKEEPER / MANAGEMENT</small></section>
    <section className="login-panel"><form onSubmit={signIn} className="login-card"><div className="login-icon"><KeyRound size={25}/></div><div className="eyebrow">WELCOME BACK</div><h2>Sign in to your workspace</h2><p>Use your management account credentials.</p>
    <label>Username<input required autoComplete="username" value={username} onChange={e => setUsername(e.target.value)} placeholder="platform-admin"/></label>
    <label>Password<input required type="password" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} placeholder="Enter password"/></label>
    {error && <div className="alert">{error}</div>}<button className="button" disabled={loading}>{loading ? 'Signing in…' : 'Sign in to GateKeeper'}</button></form></section></div>;
  const displayedOrganizations = organizations.filter(o => (o.name + o.slug).toLowerCase().includes(search.toLowerCase()));
  const displayedRoutes = routes.filter(r => (r.name + r.routeId + r.pathPattern).toLowerCase().includes(search.toLowerCase()));
  return <div className="shell"><aside className="sidebar"><div className="brand"><ShieldCheck/> gatekeeper<span>.</span></div><div className="side-label">WORKSPACE</div>
    <nav>{pages.map(p => <button key={p.id} className={page === p.id || (page === 'history' && p.id === 'routes') ? 'active' : ''} onClick={() => {setPage(p.id);setSearch('');setNotice('');setError('');}}><p.icon size={18}/>{p.title}</button>)}</nav>
    <div className="sidebar-bottom"><div className="sidebar-tip"><ShieldCheck/><strong>Control your APIs.</strong><p>Secure routing, tenant isolation, and configuration history.</p></div>
    <div className="user"><span className="avatar">{session.username[0]?.toUpperCase()}</span><div><b>{session.username}</b><small>{isPlatform ? 'Platform admin' : 'Tenant admin'}</small></div><button title="Sign out" onClick={logout}><LogOut size={18}/></button></div></div></aside>
    <main><header className="topbar"><span>Workspace <b>/ {page === 'history' ? 'Configuration history' : pages.find(p => p.id === page)?.title}</b></span><span className="service-tag"><i/> MANAGEMENT API</span></header>
    <div className="content"><div className="page-head"><div><div className="eyebrow">{isPlatform ? 'PLATFORM' : 'TENANT'} / CONTROL PLANE</div><h1>{page === 'overview' ? 'Workspace overview' : page === 'history' ? 'Configuration history' : pages.find(p=>p.id===page)?.title}</h1><p>{page === 'overview' ? 'Monitor and manage your API infrastructure.' : page === 'history' ? 'Snapshots for ' + historyName : 'Manage your workspace configuration.'}</p></div>
    <div className="actions"><button className="button outline" onClick={() => void load()}><RefreshCw size={16}/> Refresh</button>
    {page === 'organizations' && <button className="button" onClick={() => setForm({kind:'organization'})}><Plus size={16}/> New organization</button>}
    {page === 'routes' && <button className="button" onClick={() => setForm({kind:'route'})}><Plus size={16}/> Create route</button>}</div></div>
    {notice && <div className="success" role="status">{notice} <button onClick={() => setNotice('')}>×</button></div>}
    {error && <div className="alert" role="alert">{error}</div>}
    {page === 'overview' && <><div className="hero"><div className="eyebrow">GATEKEEPER PLATFORM</div><h2>Your API infrastructure,<br/><em>under control.</em></h2><p>{isPlatform ? 'Manage your tenants and their administrators.' : 'Manage upstream targets, routing policies, and changes.'}</p></div>
      <div className="stats"><div><small>{isPlatform?'ORGANIZATIONS':'API ROUTES'}</small><strong>{isPlatform?organizations.length:routes.length}</strong><span>Total configured</span></div><div><small>ACTIVE</small><strong>{isPlatform?organizations.filter(o=>o.status==='ACTIVE').length:routes.filter(r=>r.active).length}</strong><span>Currently enabled</span></div><div><small>{isPlatform?'SUSPENDED':'AUDIT EVENTS'}</small><strong>{isPlatform?organizations.filter(o=>o.status==='SUSPENDED').length:audit.length}</strong><span>{isPlatform?'Restricted tenants':'Recorded operations'}</span></div></div>
      <div className="panel"><h3>Getting started</h3><p>{isPlatform?'Use Organizations to create tenants and provision their administrators.':'Use API Routes to create policies and destinations. Version history records every configuration change.'}</p><button className="button" onClick={()=>setPage(isPlatform?'organizations':'routes')}>Open {isPlatform?'organizations':'API routes'}</button></div></>}
    {page === 'organizations' && <div className="panel"><div className="panel-top"><h3>Tenant directory</h3><input placeholder="Search organizations…" value={search} onChange={e=>setSearch(e.target.value)}/></div>
      <div className="table-wrap"><table><thead><tr><th>ORGANIZATION</th><th>SLUG</th><th>STATUS</th><th>CREATED</th><th>ACTIONS</th></tr></thead><tbody>{displayedOrganizations.map(o=><tr key={o.id}><td><b>{o.name}</b><small>#{o.id}</small></td><td><code>{o.slug}</code></td><td><select value={o.status} onChange={e=>void changeStatus(o,e.target.value as Status)}><option>ACTIVE</option><option>PENDING</option><option>SUSPENDED</option></select></td><td>{formatDate(o.createdAt)}</td><td><div className="table-actions"><button title="Tenant administrator" onClick={()=>void showAdmin(o)}><KeyRound size={17}/></button><button title="Edit" onClick={()=>setForm({kind:'organization',organization:o})}><Pencil size={17}/></button><button title="Delete" onClick={()=>void removeOrganization(o)}><Trash2 size={17}/></button></div></td></tr>)}</tbody></table></div>
      {displayedOrganizations.length===0 && <div className="empty">No organizations found.</div>}</div>}
    {page === 'routes' && <div className="panel"><div className="panel-top"><h3>Route directory</h3><input placeholder="Search API routes…" value={search} onChange={e=>setSearch(e.target.value)}/></div>
      <div className="table-wrap"><table><thead><tr><th>ROUTE</th><th>UPSTREAM</th><th>METHODS</th><th>STATUS</th><th>VERSION</th><th>ACTIONS</th></tr></thead><tbody>{displayedRoutes.map(r=><tr key={r.id}><td><b>{r.name}</b><small><code>{r.pathPattern}</code></small></td><td><code>{r.targetBaseUrl}</code></td><td>{r.allowedMethods.join(', ')}</td><td><span className={'pill '+(r.active?'on':'off')}>{r.active?'ACTIVE':'INACTIVE'}</span></td><td>v{r.configurationVersion}</td><td><div className="table-actions"><button title="Toggle status" onClick={()=>void changeRouteStatus(r)}><ShieldCheck size={17}/></button><button title="Edit" onClick={()=>setForm({kind:'route',route:r})}><Pencil size={17}/></button><button title="History" onClick={()=>void showHistory(r)}><Clock3 size={17}/></button><button title="Delete" onClick={()=>void removeRoute(r)}><Trash2 size={17}/></button></div></td></tr>)}</tbody></table></div>
      {displayedRoutes.length===0 && <div className="empty">No routes configured yet.</div>}</div>}
    {page==='history' && <div className="panel"><div className="panel-top"><h3>Version history — {historyName}</h3><button className="button outline" onClick={()=>setPage('routes')}>Back to routes</button></div>
      {versions.length?versions.map(v=><details key={v.id} className="version"><summary><b>Version {v.versionNumber}</b> · {v.changeType} · {formatDate(v.createdAt)}</summary><pre>{(()=>{try{return JSON.stringify(JSON.parse(v.configurationSnapshot),null,2);}catch{return v.configurationSnapshot;}})()}</pre></details>):<div className="empty">No history found.</div>}</div>}
    {page==='audit' && <div className="panel"><div className="panel-top"><h3>Administrative activity</h3><span>{audit.length} events</span></div><div className="table-wrap"><table><thead><tr><th>ACTION</th><th>ACTOR</th><th>RESOURCE</th><th>RESULT</th><th>DURATION</th><th>TIME</th></tr></thead><tbody>{audit.map(a=><tr key={a.id}><td><b>{a.action}</b><small>{a.details}</small></td><td>{a.actor}</td><td>{a.resourceId}</td><td><span className="pill on">{a.result}</span></td><td>{a.durationMs} ms</td><td>{formatDate(a.createdAt)}</td></tr>)}</tbody></table></div>{audit.length===0&&<div className="empty">No recorded activity yet.</div>}</div>}
    {page==='profile' && profile && <div className="panel profile"><h3>Organization details</h3><p><b>Name:</b> {profile.name}</p><p><b>Slug:</b> {profile.slug}</p><p><b>Status:</b> {profile.status}</p><button className="button outline" onClick={()=>{const name=prompt('New organization name',profile.name);if(name?.trim())void run(()=>api('/api/tenant/organization',session,'PUT',{name:name.trim()}),'Organization name updated');}}>Edit organization name</button></div>}
    </div></main>
    {form.kind==='organization' && <OrganizationDialog initial={form.organization} onClose={()=>setForm({kind:null})} onSave={v=>run(()=>api('/api/platform/organizations'+(form.organization?'/'+form.organization.id:''),session,form.organization?'PUT':'POST',v),'Organization saved')}/>}
    {form.kind==='admin' && form.organization && <AdminDialog initial={admin} name={form.organization.name} onClose={()=>setForm({kind:null})} onDelete={()=>removeAdmin(form.organization!)} onSave={v=>run(()=>api('/api/platform/organizations/'+form.organization!.id+'/tenant-admin',session,admin?'PUT':'POST',v),'Tenant administrator saved')}/>}
    {form.kind==='route' && <RouteDialog initial={form.route} slug={profile?.slug||''} onClose={()=>setForm({kind:null})} onSave={v=>run(()=>api('/api/tenant/routes'+(form.route?'/'+form.route.id:''),session,form.route?'PUT':'POST',v),'Route configuration saved')}/>}
  </div>;
}
function Dialog({title,children,onClose}:{title:string;children:React.ReactNode;onClose:()=>void}) { return <div className="overlay" onMouseDown={e=>{if(e.target===e.currentTarget)onClose();}}><section className="dialog" role="dialog" aria-modal="true" aria-label={title}><header><h2>{title}</h2><button title="Close" onClick={onClose}><X size={20}/></button></header>{children}</section></div>; }
function OrganizationDialog({initial,onClose,onSave}:{initial?:Organization;onClose:()=>void;onSave:(v:{name:string;slug:string})=>Promise<void>}) {
  const [name,setName]=useState(initial?.name||'');const [slug,setSlug]=useState(initial?.slug||'');
  const [busy,setBusy]=useState(false);return <Dialog title={initial?'Edit organization':'Create organization'} onClose={onClose}><form onSubmit={async e=>{e.preventDefault();setBusy(true);try{await onSave({name,slug});}finally{setBusy(false);}}}>
    <label>Organization name<input required maxLength={150} value={name} onChange={e=>{setName(e.target.value);if(!initial)setSlug(e.target.value.toLowerCase().trim().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,''));}}/></label>
    <label>Unique slug<input required maxLength={100} pattern="[a-z0-9]+(-[a-z0-9]+)*" value={slug} onChange={e=>setSlug(e.target.value)}/></label>
    <div className="form-actions"><button type="button" className="button outline" onClick={onClose}>Cancel</button><button className="button" disabled={busy}>{busy?'Saving…':'Save organization'}</button></div></form></Dialog>;
}
function AdminDialog({initial,name,onClose,onSave,onDelete}:{initial:TenantAdmin|null;name:string;onClose:()=>void;onSave:(v:{username:string;password:string;enabled:boolean})=>Promise<void>;onDelete:()=>Promise<void>}) {
  const [username,setUsername]=useState(initial?.username||'');const [password,setPassword]=useState('');const [enabled,setEnabled]=useState(initial?.enabled??true);
  const [busy,setBusy]=useState(false);return <Dialog title={'Tenant administrator · '+name} onClose={onClose}><form onSubmit={async e=>{e.preventDefault();setBusy(true);try{await onSave({username,password,enabled});}finally{setBusy(false);}}}>
    <label>Username<input required maxLength={100} value={username} onChange={e=>setUsername(e.target.value)}/></label><label>{initial?'New password (required)':'Password'}<input type="password" minLength={8} required value={password} onChange={e=>setPassword(e.target.value)}/></label>
    <label className="check"><input type="checkbox" checked={enabled} onChange={e=>setEnabled(e.target.checked)}/> Account enabled</label>
    <div className="form-actions">{initial&&<button type="button" className="button danger" onClick={()=>void onDelete()}>Remove admin</button>}<button className="button outline" type="button" onClick={onClose}>Cancel</button><button className="button" disabled={busy}>{busy?'Saving…':'Save administrator'}</button></div></form></Dialog>;
}
function RouteDialog({initial,slug,onClose,onSave}:{initial?:RouteData;slug:string;onClose:()=>void;onSave:(v:Omit<RouteData,'id'|'configurationVersion'>)=>Promise<void>}) {
  const [v,setV]=useState<Omit<RouteData,'id'|'configurationVersion'>>(()=>initial?{routeId:initial.routeId,name:initial.name,pathPattern:initial.pathPattern,targetBaseUrl:initial.targetBaseUrl,targetPathPrefix:initial.targetPathPrefix,requestsPerMinute:initial.requestsPerMinute,responseTimeoutMs:initial.responseTimeoutMs,idempotencyEnabled:initial.idempotencyEnabled,active:initial.active,allowedMethods:initial.allowedMethods,headerRules:initial.headerRules}: {...baseRoute(),pathPattern:slug?'/'+slug+'/api/**':''});
  const [busy,setBusy]=useState(false);const update=(patch:Partial<typeof v>)=>setV(old=>({...old,...patch}));
  return <Dialog title={initial?'Edit API route':'Create API route'} onClose={onClose}><form onSubmit={async e=>{e.preventDefault();setBusy(true);try{await onSave(v);}finally{setBusy(false);}}}>
    <div className="form-grid"><label>Route ID<input required disabled={!!initial} value={v.routeId} onChange={e=>update({routeId:e.target.value})} placeholder="orders-api"/></label><label>Display name<input required value={v.name} onChange={e=>update({name:e.target.value})}/></label>
    <label>Path pattern<input required value={v.pathPattern} onChange={e=>update({pathPattern:e.target.value})} placeholder={'/'+(slug||'tenant')+'/api/**'}/></label>
    <label>Target base URL<input type="url" required value={v.targetBaseUrl} onChange={e=>update({targetBaseUrl:e.target.value})} placeholder="https://api.example.com"/></label>
    <label>Target path prefix<input required value={v.targetPathPrefix} onChange={e=>update({targetPathPrefix:e.target.value})}/></label>
    <label>Response timeout (ms)<input type="number" min="1" required value={v.responseTimeoutMs} onChange={e=>update({responseTimeoutMs:Number(e.target.value)})}/></label>
    <label>Requests/minute (optional)<input type="number" min="1" value={v.requestsPerMinute??''} onChange={e=>update({requestsPerMinute:e.target.value?Number(e.target.value):null})}/></label></div>
    <h3>Allowed HTTP methods</h3><div className="method-row">{methods.map(m=><label key={m} className="method"><input type="checkbox" checked={v.allowedMethods.includes(m)} onChange={e=>update({allowedMethods:e.target.checked?[...v.allowedMethods,m]:v.allowedMethods.filter(x=>x!==m)})}/>{m}</label>)}</div>
    <div className="switches"><label className="check"><input type="checkbox" checked={v.active} onChange={e=>update({active:e.target.checked})}/> Route active</label><label className="check"><input type="checkbox" checked={v.idempotencyEnabled} onChange={e=>update({idempotencyEnabled:e.target.checked})}/> Idempotency enabled</label></div>
    <div className="split"><h3>Header rules</h3><button type="button" className="button outline" onClick={()=>update({headerRules:[...v.headerRules,{direction:'REQUEST',action:'ADD',headerName:'',headerValue:''}]})}><Plus size={14}/> Add rule</button></div>
    {v.headerRules.map((rule,i)=><div key={i} className="rule"><div className="form-grid"><label>Direction<select value={rule.direction} onChange={e=>update({headerRules:v.headerRules.map((r,j)=>i===j?{...r,direction:e.target.value}:r)})}><option>REQUEST</option><option>RESPONSE</option></select></label>
    <label>Action<select value={rule.action} onChange={e=>update({headerRules:v.headerRules.map((r,j)=>i===j?{...r,action:e.target.value,headerValue:e.target.value==='REMOVE'?null:r.headerValue}:r)})}><option>ADD</option><option>REMOVE</option></select></label>
    <label>Header name<input required value={rule.headerName} onChange={e=>update({headerRules:v.headerRules.map((r,j)=>i===j?{...r,headerName:e.target.value}:r)})}/></label>
    <label>Header value<input disabled={rule.action==='REMOVE'} required={rule.action==='ADD'} value={rule.headerValue||''} onChange={e=>update({headerRules:v.headerRules.map((r,j)=>i===j?{...r,headerValue:e.target.value}:r)})}/></label></div><button type="button" className="button outline" onClick={()=>update({headerRules:v.headerRules.filter((_,j)=>j!==i)})}>Remove rule</button></div>)}
    <div className="form-actions"><button type="button" className="button outline" onClick={onClose}>Cancel</button><button className="button" disabled={busy||v.allowedMethods.length===0}>{busy?'Saving…':'Save route'}</button></div></form></Dialog>;
}
createRoot(document.getElementById('root')!).render(<React.StrictMode><App/></React.StrictMode>);
