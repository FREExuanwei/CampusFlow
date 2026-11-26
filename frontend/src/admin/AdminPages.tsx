import { useEffect, useRef, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { Link, Navigate, Route, Routes, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  ArrowLeft, ArrowUpRight, CalendarDays, ChevronRight, ClipboardCheck,
  ClipboardList, Clock3, FileText, ListFilter, MapPin, Plus, Search, Send, Ticket, Users,
} from 'lucide-react';
import { api, categories, Pagination, QueryState, queryString, useQuery, useToast } from '../core';
import type { Category, Page, User } from '../core';
import './admin.css';

const activityStatuses = {
  DRAFT: '草稿', SUBMITTED: '待审核', APPROVED: '审核通过', REJECTED: '已拒绝', PUBLISHED: '已发布',
};
const userStatuses = { ACTIVE: '正常', DISABLED: '已禁用' };
const enrollmentStatuses = { ENROLLED: '已报名', CANCELLED: '已取消' };
const roles: Record<string, string> = { ADMIN: '管理员', STUDENT: '学生' };
const auditActions = {
  ACTIVITY_CREATED: '创建活动', ACTIVITY_EDITED: '编辑活动', ACTIVITY_SUBMITTED: '提交审核',
  ACTIVITY_APPROVED: '审核通过', ACTIVITY_REJECTED: '审核拒绝', ACTIVITY_PUBLISHED: '发布活动',
  ENROLLMENT_CREATED: '活动报名', ENROLLMENT_CANCELLED: '取消报名', USER_STATUS_CHANGED: '变更账号状态',
};
const targetTypes: Record<string, string> = { ACTIVITY: '活动', USER: '用户', ENROLLMENT: '报名记录' };
const statuses = Object.keys(activityStatuses) as Array<keyof typeof activityStatuses>;
const numberFormat = new Intl.NumberFormat('zh-CN');
const timeFormat = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' });
const pageSize = 10;

type ActivityStatus = keyof typeof activityStatuses;
type EnrollmentStatus = keyof typeof enrollmentStatuses;
type AuditAction = keyof typeof auditActions;
type Activity = {
  id: number; title: string; description: string; location: string; startsAt: string; endsAt: string;
  capacity: number; category: Category; status: ActivityStatus; createdBy: number; createdAt: string;
  updatedAt: string; version: number; reviewedBy: number | null; reviewedAt: string | null;
  reviewReason: string | null; publishedAt: string | null;
};
type Statistics = {
  users: number; activities: number; publishedActivities: number; pendingActivities: number;
  activeEnrollments: number; cancelledEnrollments: number; totalEnrollments: number;
  activityStatusCounts: Record<ActivityStatus, number>;
};
type Enrollment = {
  id: number; userId: number; username: string; status: EnrollmentStatus; enrolledAt: string;
  cancelledAt: string | null;
};
type RosterSummary = { total: number; active: number; cancelled: number };
type Operation = { id: number; actorId: number; action: AuditAction; targetType: string; targetId: number; createdAt: string };
type Result<T> = { data: T | undefined; error: string; loading: boolean; reload(): void };

const activityPath = (id: number | string) => `/admin/activities/${encodeURIComponent(id)}`;
const userPath = (id: number) => `/admin/users/${id}`;
const formattedTime = (value?: string | null) => value ? timeFormat.format(new Date(value)) : '—';

function pageUrl(path: string, page: number, filters: Record<string, string>, size = pageSize) {
  return `${path}${queryString({ page, size, ...filters })}`;
}

function useListFilters() {
  const [params, setParams] = useSearchParams();
  const page = Number(params.get('page') ?? 0);
  function change(key: string, value: string) {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== 'page') next.delete('page');
    setParams(next);
  }

  return {
    page: Number.isInteger(page) && page >= 0 && page <= 1000000 ? page : 0,
    value: (key: string) => params.get(key) ?? '',
    change,
  };
}

function ResultArea<T>({ result, children }: { result: Result<T>; children: (data: T) => ReactNode }) {
  if (result.loading || result.error) return <div className="panel adm-state">
    <QueryState loading={result.loading} error={result.error} retry={result.reload}>{null}</QueryState>
  </div>;
  return result.data === undefined ? null : children(result.data);
}

function Badge({ code, children }: { code: string; children: ReactNode }) {
  return <span className={`tag adm-tag adm-${code.toLowerCase()}`}>{children}</span>;
}

function AdminTable({ labels, title, children }: { labels: string[]; title: string; children: ReactNode }) {
  return <div className="table-wrap adm-table-wrap"><table role="table" aria-label={title}>
    <thead role="rowgroup"><tr role="row">{labels.map((label) => <th role="columnheader" key={label} scope="col">{label}</th>)}</tr></thead>
    <tbody role="rowgroup">{children}</tbody>
  </table></div>;
}

function TableCell({ label, children }: { label: string; children: ReactNode }) {
  return <td role="cell" data-label={label}><div>{children}</div></td>;
}

function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return <div className="empty adm-empty"><FileText size={25} /><strong>{title}</strong>{children}</div>;
}

function Filter({ label, value, choices, onChange }: {
  label: string; value: string; choices: Record<string, string>; onChange: (value: string) => void;
}) {
  return <label className="field adm-filter"><span>{label}</span>
    <select className="input" value={value} onChange={(event) => onChange(event.target.value)}>
      <option value="">全部</option>
      {Object.entries(choices).map(([value, text]) => <option value={value} key={value}>{text}</option>)}
    </select>
  </label>;
}

function SearchBox({ label, value, maxLength, placeholder, pattern, onSearch }: {
  label: string; value: string; maxLength: number; placeholder: string; pattern?: string;
  onSearch: (text: string) => void;
}) {
  const [draft, setDraft] = useState(value);
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSearch(draft.trim());
  }

  return <form className="adm-search" onSubmit={submit} role="search">
    <label className="field"><span>{label}</span><input className="input" type="search"
      placeholder={placeholder} value={draft} pattern={pattern} maxLength={maxLength}
      onChange={(event) => setDraft(event.target.value)} /></label>
    <button className="btn" type="submit"><Search size={16} /> 搜索</button>
  </form>;
}

function Back({ to, children }: { to: string; children: ReactNode }) {
  return <Link className="adm-back" to={to}><ArrowLeft size={16} />{children}</Link>;
}

function Dashboard() {
  const statistics = useQuery(() => api<Statistics>('/admin/statistics'), []);
  const queue = useQuery(() => api<Page<Activity>>(pageUrl('/admin/activities', 0, { status: 'SUBMITTED' }, 4)), []);
  return <div className="adm-stack">
    <ResultArea result={statistics}>{(stats) => {
      const metrics = [
        { label: '用户总数', value: stats.users, icon: Users, tone: 'blue', to: '/admin/users' },
        { label: '活动总数', value: stats.activities, icon: CalendarDays, tone: 'violet', to: '/admin/activities' },
        { label: '待审核活动', value: stats.pendingActivities, icon: ClipboardCheck, tone: 'amber', to: '/admin/review' },
        { label: '已发布活动', value: stats.publishedActivities, icon: Send, tone: 'green', to: '/admin/activities?status=PUBLISHED' },
        { label: '报名总数', value: stats.totalEnrollments, icon: Ticket, tone: 'pink', to: '#enrollment-overview' },
      ];
      return <>
        <div className="adm-metrics">
          {metrics.map(({ label, value, icon: Icon, tone, to }) => {
            const card = <><span className={`adm-metric-icon adm-metric-${tone}`}><Icon size={19} /></span>
              <span>{label}</span><strong>{numberFormat.format(value)}</strong><ArrowUpRight className="adm-metric-arrow" size={16} /></>;
            return to.startsWith('#')
              ? <a className="panel adm-metric" href={to} key={label}>{card}</a>
              : <Link className="panel adm-metric" to={to} key={label}>{card}</Link>;
          })}
        </div>
        <div className="adm-dashboard-grid">
          <section className="panel adm-status-card" aria-labelledby="activity-overview-title">
            <div className="adm-card-heading">
              <h2 id="activity-overview-title">活动状态分布</h2><span>共 {numberFormat.format(stats.activities)} 场</span>
            </div>
            <div className="adm-bars">
              {statuses.map((status) => {
                const count = stats.activityStatusCounts[status] ?? 0;
                return <Link to={`/admin/activities?status=${status}`} className="adm-bar-row" key={status}>
                  <span>{activityStatuses[status]}</span>
                  <span className="adm-bar-track" aria-hidden="true"><span className={`adm-bar-${status.toLowerCase()}`}
                    style={{ width: `${stats.activities > 0 ? count / stats.activities * 100 : 0}%` }} /></span>
                  <strong>{numberFormat.format(count)}</strong>
                </Link>;
              })}
            </div>
          </section>
          <section className="panel adm-participation" id="enrollment-overview" aria-labelledby="enrollment-overview-title">
            <div className="adm-card-heading"><h2 id="enrollment-overview-title">活动参与情况</h2><Ticket size={19} /></div>
            <dl>
              <div><dt><i className="adm-dot adm-dot-active" />有效报名</dt><dd>{numberFormat.format(stats.activeEnrollments)}</dd></div>
              <div><dt><i className="adm-dot adm-dot-cancelled" />已取消报名</dt><dd>{numberFormat.format(stats.cancelledEnrollments)}</dd></div>
            </dl>
            <Link className="adm-quiet-link" to="/admin/activities?status=PUBLISHED">查看已发布活动的名册 <ArrowUpRight size={16} /></Link>
          </section>
        </div>
      </>;
    }}</ResultArea>
    <section className="panel adm-queue" aria-labelledby="dashboard-queue-title">
      <div className="adm-card-heading"><h2 id="dashboard-queue-title">待审核活动</h2>
        <Link className="adm-quiet-link" to="/admin/review">查看审核列表 <ArrowUpRight size={16} /></Link></div>
      <ResultArea result={queue}>{({ items }) => items.length > 0 ? <ul className="adm-queue-list">
        {items.map((activity) => <li key={activity.id}><Link to={`${activityPath(activity.id)}?from=review`}>
          <span className="adm-queue-icon"><ClipboardList size={17} /></span>
          <span><strong>{activity.title}</strong><small>{categories[activity.category]}　{formattedTime(activity.startsAt)}</small></span>
          <ChevronRight size={17} />
        </Link></li>)}
      </ul> : <Empty title="暂无待审核活动" />}</ResultArea>
    </section>
  </div>;
}

function Activities({ review = false }: { review?: boolean }) {
  const filters = useListFilters();
  const title = filters.value('title');
  const selectedStatus = filters.value('status');
  const status = review ? 'SUBMITTED' : statuses.find((item) => item === selectedStatus) ?? '';
  const list = useQuery(() => api<Page<Activity>>(pageUrl('/admin/activities', filters.page, { title, status })),
    [title, status, filters.page]);

  return <div className="adm-stack">
    <section className="panel adm-list">
      <div className="toolbar adm-toolbar">
        <SearchBox key={title} label="活动名称" value={title} placeholder="搜索活动名称" maxLength={120}
          onSearch={(value) => filters.change('title', value)} />
        {!review && <>
          <Filter label="活动状态" value={status} choices={activityStatuses} onChange={(value) => filters.change('status', value)} />
          <Link className="btn primary adm-toolbar-action" to="/admin/activities/new"><Plus size={17} /> 新建活动</Link>
        </>}
      </div>
      <ResultArea result={list}>{(page) => <>
        {page.items.length > 0 ? <AdminTable title={review ? '待审核活动' : '活动列表'}
          labels={['活动名称', '分类', '开始时间', '状态', '报名上限', '操作']}>
          {page.items.map((activity) => <tr role="row" key={activity.id}>
            <TableCell label="活动名称"><Link className="adm-row-title" to={`${activityPath(activity.id)}${review ? '?from=review' : ''}`}>{activity.title}</Link>
              <small>{activity.location}</small></TableCell>
            <TableCell label="分类">{categories[activity.category]}</TableCell>
            <TableCell label="开始时间">{formattedTime(activity.startsAt)}</TableCell>
            <TableCell label="状态"><Badge code={activity.status}>{activityStatuses[activity.status]}</Badge></TableCell>
            <TableCell label="报名上限">{numberFormat.format(activity.capacity)} 人</TableCell>
            <TableCell label="操作"><div className="adm-row-actions">
              {review
                ? <Link to={`${activityPath(activity.id)}?from=review`}>审核活动</Link>
                : <><Link to={activityPath(activity.id)}>详情</Link><Link to={`${activityPath(activity.id)}/enrollments`}>报名名册</Link></>}
            </div></TableCell>
          </tr>)}
        </AdminTable> : <Empty title={review ? '暂无符合条件的待审核活动' : '暂无符合条件的活动'} />}
        <Pagination result={page} change={(page) => filters.change('page', String(page))} />
      </>}</ResultArea>
    </section>
  </div>;
}

type Confirmation = {
  title: string; description: string; confirmLabel: string; danger?: boolean;
  reason?: 'optional' | 'required'; apply: (reason: string) => Promise<void>;
};

function ConfirmDialog({ action, close }: { action: Confirmation; close: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const pending = useRef(false);
  const [busy, setBusy] = useState(false);
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    const element = dialog.current;
    if (element && !element.open) {
      if (typeof element.showModal === 'function') element.showModal();
      else element.setAttribute('open', '');
    }
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending.current) return;
    if (action.reason === 'required' && !reason.trim()) {
      setError('请填写审核意见。');
      return;
    }
    pending.current = true;
    setError('');
    setBusy(true);
    try {
      await action.apply(reason.trim());
      close();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '操作失败，请重试。');
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  return <dialog className="adm-dialog" ref={dialog} aria-labelledby="adm-dialog-title" onCancel={(event) => {
    event.preventDefault();
    if (!pending.current) close();
  }}>
    <form onSubmit={submit}>
      <h2 id="adm-dialog-title">{action.title}</h2><p>{action.description}</p>
      {action.reason && <label className="field"><span>审核意见{action.reason === 'optional' && <small>（选填）</small>}</span>
        <textarea className="input" rows={4} maxLength={500} required={action.reason === 'required'} value={reason}
          onChange={(event) => setReason(event.target.value)} placeholder="填写审核意见" /></label>}
      {error && <p className="adm-form-error" role="alert">{error}</p>}
      <div className="adm-actions"><button className="btn" type="button" disabled={busy} onClick={close}>取消</button>
        <button className={`btn ${action.danger ? 'danger' : 'primary'}`} disabled={busy}>{busy ? '正在处理…' : action.confirmLabel}</button></div>
    </form>
  </dialog>;
}

function DetailItems({ items }: { items: { label: string; value: ReactNode }[] }) {
  return <dl className="adm-detail-items">{items.map(({ label, value }) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>;
}

function ActivityDetail() {
  const { id = '' } = useParams();
  const [params] = useSearchParams();
  const path = activityPath(id);
  const fromReview = params.get('from') === 'review';
  const activity = useQuery(() => api<Activity>(path), [path]);
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);
  const toast = useToast();
  return <div className="adm-stack">
    <Back to={fromReview ? '/admin/review' : '/admin/activities'}>{fromReview ? '返回审核列表' : '返回活动列表'}</Back>
    <ResultArea result={activity}>{(record) => {
      const transition = (endpoint: string, success: string, options: Omit<Confirmation, 'apply'>, decision?: 'APPROVE' | 'REJECT') => {
        setConfirmation({ ...options, apply: async (reason) => {
          const body = decision ? { version: record.version, decision, reason: reason || null } : { version: record.version };
          await api<Activity>(`${path}/${endpoint}`, { method: 'POST', body: JSON.stringify(body) });
          toast(success);
          activity.reload();
        } });
      };
      return <>
        <section className="panel adm-record">
          <div className="adm-record-top">
            <div className="adm-record-title"><div className="adm-record-tags">
              <Badge code={record.status}>{activityStatuses[record.status]}</Badge><span>{categories[record.category]}</span>
            </div><h2>{record.title}</h2></div>
            <div className="adm-actions">
              <Link className="btn" to={`${path}/enrollments`}><Users size={16} /> 报名名册</Link>
              {(record.status === 'DRAFT' || record.status === 'REJECTED') && <Link className="btn" to={`${path}/edit`}>编辑活动</Link>}
              {record.status === 'DRAFT' && <button className="btn primary" onClick={() => transition('submit', '活动已提交审核', {
                title: '提交审核', description: '提交后，活动将进入待审核列表。', confirmLabel: '确认提交',
              })}>提交审核</button>}
              {record.status === 'SUBMITTED' && <>
                <button className="btn danger" onClick={() => transition('review', '活动审核已拒绝', {
                  title: '审核拒绝', description: '拒绝后可修改活动并重新提交审核。', confirmLabel: '确认拒绝', danger: true, reason: 'required',
                }, 'REJECT')}>审核拒绝</button>
                <button className="btn primary" onClick={() => transition('review', '活动审核已通过', {
                  title: '审核通过', description: '审核通过后可发布活动。', confirmLabel: '确认通过', reason: 'optional',
                }, 'APPROVE')}>审核通过</button>
              </>}
              {record.status === 'APPROVED' && <button className="btn primary" onClick={() => transition('publish', '活动已发布', {
                title: '发布活动', description: '发布后，学生可在活动广场查看活动并报名。', confirmLabel: '确认发布',
              })}>发布活动</button>}
            </div>
          </div>
          <div className="adm-record-meta">
            <span><MapPin size={17} />{record.location}</span>
            <span><Clock3 size={17} />{formattedTime(record.startsAt)} — {formattedTime(record.endsAt)}</span>
            <span><Users size={17} />报名上限 {numberFormat.format(record.capacity)} 人</span>
          </div>
          <section className="adm-description"><h3>活动介绍</h3><p>{record.description}</p></section>
        </section>
        <section className="panel adm-record">
          <h2>活动记录</h2>
          <DetailItems items={[
            { label: '创建账号', value: <Link className="adm-quiet-link" to={userPath(record.createdBy)}>用户 #{record.createdBy}</Link> },
            { label: '创建时间', value: formattedTime(record.createdAt) }, { label: '更新时间', value: formattedTime(record.updatedAt) },
            ...(record.reviewedAt ? [{ label: '审核时间', value: formattedTime(record.reviewedAt) },
              { label: '审核意见', value: record.reviewReason || '未填写' }] : []),
            ...(record.reviewedBy !== null ? [{ label: '审核账号', value: <Link className="adm-quiet-link" to={userPath(record.reviewedBy)}>用户 #{record.reviewedBy}</Link> }] : []),
            ...(record.publishedAt ? [{ label: '发布时间', value: formattedTime(record.publishedAt) }] : []),
          ]} />
        </section>
      </>;
    }}</ResultArea>
    {confirmation && <ConfirmDialog action={confirmation} close={() => setConfirmation(null)} />}
  </div>;
}

function dateInput(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 19);
}

function ActivityForm({ activity }: { activity?: Activity }) {
  const navigate = useNavigate();
  const toast = useToast();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const back = activity ? activityPath(activity.id) : '/admin/activities';

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending.current) return;
    const form = new FormData(event.currentTarget);
    const title = String(form.get('title')).trim();
    const description = String(form.get('description')).trim();
    const location = String(form.get('location')).trim();
    const category = String(form.get('category'));
    const startInput = String(form.get('startsAt'));
    const endInput = String(form.get('endsAt'));
    const startsAt = new Date(startInput);
    const endsAt = new Date(endInput);
    const capacity = Number(form.get('capacity'));
    if (!title || !location || !description) return setError('请填写活动名称、地点和介绍。');
    if (!(startsAt.getTime() > Date.now())) return setError('开始时间必须晚于当前时间。');
    if (!(endsAt.getTime() > startsAt.getTime())) return setError('结束时间必须晚于开始时间。');
    if (!Number.isInteger(capacity) || capacity < 1 || capacity > 2147483647) return setError('报名上限必须是 1 至 2147483647 之间的整数。');
    pending.current = true;
    setError('');
    setBusy(true);
    try {
      const result = await api<Activity>(activity ? activityPath(activity.id) : '/admin/activities', {
        method: activity ? 'PATCH' : 'POST',
        body: JSON.stringify({ title, description, location, category, capacity,
          startsAt: activity && Date.parse(dateInput(activity.startsAt)) === startsAt.getTime() ? activity.startsAt : startsAt.toISOString(),
          endsAt: activity && Date.parse(dateInput(activity.endsAt)) === endsAt.getTime() ? activity.endsAt : endsAt.toISOString(),
          ...(activity ? { version: activity.version } : {}),
        }),
      });
      toast(activity ? '活动已保存' : '活动草稿已创建');
      navigate(activityPath(result.id), { replace: true });
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '保存失败，请重试。');
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  return <div className="adm-stack">
    <Back to={back}>{activity ? '返回活动详情' : '返回活动列表'}</Back>
    <form className="panel adm-record adm-activity-form" onSubmit={save}>
      <h2>活动基本信息</h2>
      <div className="adm-form-grid">
        <label className="field"><span>活动名称</span><input className="input" name="title" defaultValue={activity?.title}
          maxLength={120} required placeholder="例如 校园创意市集" /></label>
        <label className="field"><span>活动分类</span><select className="input" name="category" defaultValue={activity?.category ?? 'GENERAL'}>
          {Object.entries(categories).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
        </select></label>
        <label className="field"><span>活动地点</span><input className="input" name="location" defaultValue={activity?.location}
          maxLength={160} required placeholder="填写校区、场馆或教室" /></label>
        <label className="field"><span>报名上限</span><input className="input" name="capacity" defaultValue={activity?.capacity}
          type="number" min={1} max={2147483647} step={1} required /></label>
        <label className="field"><span>开始时间</span><input className="input" name="startsAt" defaultValue={dateInput(activity?.startsAt)} type="datetime-local" step="1" required /></label>
        <label className="field"><span>结束时间</span><input className="input" name="endsAt" defaultValue={dateInput(activity?.endsAt)} type="datetime-local" step="1" required /></label>
        <label className="field adm-wide"><span>活动介绍</span><textarea className="input" name="description" rows={6}
          defaultValue={activity?.description} maxLength={2000} required placeholder="介绍活动安排与报名要求" /></label>
      </div>
      {error && <p className="adm-form-error" role="alert">{error}</p>}
      <div className="adm-actions"><Link className="btn" to={back}>取消</Link>
        <button className="btn primary" disabled={busy}>{busy ? '正在保存…' : '保存草稿'}</button></div>
    </form>
  </div>;
}

function EditActivity() {
  const { id = '' } = useParams();
  const path = activityPath(id);
  const query = useQuery(() => api<Activity>(path), [path]);
  return <ResultArea result={query}>{(activity) => activity.status === 'DRAFT' || activity.status === 'REJECTED'
    ? <ActivityForm activity={activity} key={activity.id} /> : <Navigate to={path} replace />}</ResultArea>;
}

function Roster() {
  const { id = '' } = useParams();
  const path = activityPath(id);
  const filters = useListFilters();
  const status = Object.keys(enrollmentStatuses).find((item) => item === filters.value('status')) ?? '';
  const overview = useQuery(() => Promise.all([api<Activity>(path), api<RosterSummary>(`${path}/enrollments/summary`)]), [path]);
  const roster = useQuery(() => api<Page<Enrollment>>(pageUrl(`${path}/enrollments`, filters.page, { status })), [path, status, filters.page]);

  return <div className="adm-stack">
    <Back to={path}>返回活动详情</Back>
    <ResultArea result={overview}>{([activity, summary]) => <section className="panel adm-record">
      <div className="adm-card-heading"><h2>{activity.title}</h2><Badge code={activity.status}>{activityStatuses[activity.status]}</Badge></div>
      <DetailItems items={[
        { label: '报名记录', value: numberFormat.format(summary.total) },
        { label: '有效报名', value: numberFormat.format(summary.active) },
        { label: '已取消报名', value: numberFormat.format(summary.cancelled) },
        { label: '剩余名额', value: numberFormat.format(Math.max(0, activity.capacity - summary.active)) },
      ]} />
    </section>}</ResultArea>
    <section className="panel adm-list">
      <div className="toolbar adm-toolbar"><Filter label="报名状态" value={status} choices={enrollmentStatuses} onChange={(value) => filters.change('status', value)} /></div>
      <ResultArea result={roster}>{(page) => <>
        {page.items.length > 0 ? <AdminTable title="报名名册" labels={['用户名', '报名状态', '报名时间', '取消时间']}>
          {page.items.map((row) => <tr role="row" key={row.id}>
            <TableCell label="用户名"><span className="adm-row-title">{row.username}</span></TableCell>
            <TableCell label="报名状态"><Badge code={row.status}>{enrollmentStatuses[row.status]}</Badge></TableCell>
            <TableCell label="报名时间">{formattedTime(row.enrolledAt)}</TableCell>
            <TableCell label="取消时间">{formattedTime(row.cancelledAt)}</TableCell>
          </tr>)}
        </AdminTable> : <Empty title="暂无符合条件的报名记录" />}
        <Pagination result={page} change={(page) => filters.change('page', String(page))} />
      </>}</ResultArea>
    </section>
  </div>;
}

function RoleLabels({ value }: { value: string[] }) {
  return <div className="adm-roles">{value.map((role) => <Badge key={role} code={role}>{roles[role] ?? role}</Badge>)}</div>;
}

function UserList() {
  const filters = useListFilters();
  const username = filters.value('username');
  const status = Object.keys(userStatuses).find((item) => item === filters.value('status')) ?? '';
  const users = useQuery(() => api<Page<User>>(pageUrl('/admin/users', filters.page, { username, status })),
    [username, status, filters.page]);

  return <section className="panel adm-list">
    <div className="toolbar adm-toolbar">
      <SearchBox key={username} label="用户名" value={username} placeholder="搜索用户名" maxLength={32} pattern="[A-Za-z0-9_]{1,32}"
        onSearch={(value) => filters.change('username', value)} />
      <Filter label="账号状态" value={status} choices={userStatuses} onChange={(value) => filters.change('status', value)} />
    </div>
    <ResultArea result={users}>{(page) => <>
      {page.items.length > 0 ? <AdminTable title="用户列表" labels={['用户', '角色', '账号状态', '注册时间', '操作']}>
        {page.items.map((user) => <tr role="row" key={user.id}>
          <TableCell label="用户"><Link className="adm-row-title" to={userPath(user.id)}>{user.displayName}</Link><small>{user.username}</small></TableCell>
          <TableCell label="角色"><RoleLabels value={user.roles} /></TableCell>
          <TableCell label="账号状态"><Badge code={user.status}>{userStatuses[user.status]}</Badge></TableCell>
          <TableCell label="注册时间">{formattedTime(user.createdAt)}</TableCell>
          <TableCell label="操作"><div className="adm-row-actions"><Link to={userPath(user.id)}>查看用户</Link></div></TableCell>
        </tr>)}
      </AdminTable> : <Empty title="暂无符合条件的用户" />}
      <Pagination result={page} change={(page) => filters.change('page', String(page))} />
    </>}</ResultArea>
  </section>;
}

function UserDetail() {
  const { id = '' } = useParams();
  const path = `/admin/users/${encodeURIComponent(id)}`;
  const profile = useQuery(() => api<User>(path), [path]);
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);
  const toast = useToast();

  return <div className="adm-stack">
    <Back to="/admin/users">返回用户列表</Back>
    <ResultArea result={profile}>{(user) => {
      const disabling = user.status === 'ACTIVE';
      const label = disabling ? '禁用账号' : '恢复账号';
      const confirm = () => setConfirmation({
        title: label,
        description: disabling ? `禁用 ${user.username} 后，该账号将退出登录并无法再次登录。` : `恢复后，${user.username} 可以重新登录。`,
        confirmLabel: disabling ? '确认禁用' : '确认恢复',
        danger: disabling,
        apply: async () => {
          await api<User>(`${path}/status`, { method: 'PATCH', body: JSON.stringify({ status: disabling ? 'DISABLED' : 'ACTIVE' }) });
          toast(disabling ? '账号已禁用' : '账号已恢复');
          profile.reload();
        },
      });

      return <section className="panel adm-record">
        <div className="adm-record-top">
          <div className="adm-profile-heading"><span className="adm-avatar"><Users size={24} /></span>
            <div className="adm-record-title"><h2>{user.displayName}</h2><span className="muted">@{user.username}</span></div>
          </div>
          <button className={`btn ${disabling ? 'danger' : 'primary'}`} onClick={confirm}>{label}</button>
        </div>
        <DetailItems items={[
          { label: '用户名', value: user.username }, { label: '用户编号', value: `#${user.id}` },
          { label: '账号状态', value: <Badge code={user.status}>{userStatuses[user.status]}</Badge> },
          { label: '角色（只读）', value: <RoleLabels value={user.roles} /> },
          { label: '注册时间', value: formattedTime(user.createdAt) },
        ]} />
      </section>;
    }}</ResultArea>
    {confirmation && <ConfirmDialog action={confirmation} close={() => setConfirmation(null)} />}
  </div>;
}

function Audit() {
  const filters = useListFilters();
  const action = Object.keys(auditActions).find((item) => item === filters.value('action')) ?? '';
  const logs = useQuery(() => api<Page<Operation>>(pageUrl('/admin/operation-logs', filters.page, { action })),
    [action, filters.page]);

  return <section className="panel adm-list">
    <div className="toolbar adm-toolbar"><ListFilter size={18} aria-hidden="true" />
      <Filter label="操作类型" value={action} choices={auditActions} onChange={(value) => filters.change('action', value)} /></div>
    <ResultArea result={logs}>{(page) => <>
      {page.items.length > 0 ? <AdminTable title="操作日志" labels={['操作时间', '操作类型', '操作人', '业务对象']}>
        {page.items.map((operation) => {
          const target = operation.targetType === 'ACTIVITY' ? activityPath(operation.targetId)
            : operation.targetType === 'USER' ? userPath(operation.targetId) : '';
          const object = `${targetTypes[operation.targetType] ?? operation.targetType} #${operation.targetId}`;
          return <tr role="row" key={operation.id}>
            <TableCell label="操作时间">{formattedTime(operation.createdAt)}</TableCell>
            <TableCell label="操作类型"><span className="adm-row-title">{auditActions[operation.action] ?? operation.action}</span></TableCell>
            <TableCell label="操作人"><Link className="adm-quiet-link" to={userPath(operation.actorId)}>用户 #{operation.actorId}</Link></TableCell>
            <TableCell label="业务对象">{target ? <Link className="adm-quiet-link" to={target}>{object}</Link> : object}</TableCell>
          </tr>;
        })}
      </AdminTable> : <Empty title="暂无符合条件的操作记录" />}
      <Pagination result={page} change={(page) => filters.change('page', String(page))} />
    </>}</ResultArea>
  </section>;
}

export default function AdminPages() {
  const { pathname } = useLocation();
  return <div className="admin-pages"><Routes key={pathname}>
    <Route index element={<Dashboard />} />
    <Route path="activities" element={<Activities />} />
    <Route path="activities/new" element={<ActivityForm />} />
    <Route path="activities/:id" element={<ActivityDetail />} />
    <Route path="activities/:id/edit" element={<EditActivity />} />
    <Route path="activities/:id/enrollments" element={<Roster />} />
    <Route path="review" element={<Activities review />} />
    <Route path="users" element={<UserList />} />
    <Route path="users/:id" element={<UserDetail />} />
    <Route path="audit" element={<Audit />} />
    <Route path="*" element={<Navigate to="/admin" replace />} />
  </Routes></div>;
}
