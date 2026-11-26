import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, Route, Routes, useParams } from 'react-router-dom'
import {
  ArrowLeft, ArrowRight, Bell, BookOpen, Calendar, CalendarCheck, CalendarDays, Check, ChevronRight,
  Clock3, Compass, MapPin, Search, SlidersHorizontal, Ticket, UsersRound, X,
} from 'lucide-react'
import {
  api, ApiError, categories, ConfirmDialog, dateTime, errorText, Pagination, phases, QueryState,
  queryString, useAuth, useQuery, useToast,
  type Activity, type Category, type Enrollment, type MyEnrollment, type Notice, type Page, type Phase, type User,
} from '../core'
import './student.css'

const BASE = '/student'
const categoryImages: Record<Category, string> = {
  GENERAL: '/media/campus.jpg', ACADEMIC: '/media/academic.jpg', CULTURE: '/media/culture.jpg',
  SPORTS: '/media/sports.jpg', VOLUNTEER: '/media/culture.jpg',
}

function PageTitle({ title, description, children }: { title: string; description?: string; children?: ReactNode }) {
  return <div className="page-title"><div><h1>{title}</h1>{description && <p>{description}</p>}</div>{children}</div>
}

function ActivityCard({ activity }: { activity: Activity }) {
  return <Link className="activity-card" to={`${BASE}/activities/${activity.id}`} data-testid="activity-card">
    <div className="activity-cover" style={{ backgroundImage: `url("${categoryImages[activity.category]}")` }}>
      <span className={`tag category category-${activity.category.toLowerCase()}`}>{categories[activity.category]}</span>
    </div>
    <div className="activity-card-body">
      <h3>{activity.title}</h3>
      <p className="activity-summary">{activity.description}</p>
      <div className="event-meta"><Calendar size={15} /><span>{dateTime(activity.startsAt)}</span></div>
      <div className="event-meta"><MapPin size={15} /><span>{activity.location}</span></div>
      <div className="activity-card-footer">
        <span className={activity.remainingSeats ? 'availability' : 'availability full'}><i />{activity.phase === 'UPCOMING'
          ? activity.remainingSeats ? `余 ${activity.remainingSeats} 个名额` : '名额已满'
          : phases[activity.phase]}</span>
        <ChevronRight size={17} />
      </div>
    </div>
  </Link>
}

function Dashboard() {
  const { user } = useAuth()
  const data = useQuery(async () => {
    const [upcoming, enrolled, unread] = await Promise.all([
      api<Page<Activity>>('/activities?phase=UPCOMING&size=9'),
      api<Page<MyEnrollment>>('/enrollments/me?status=ENROLLED&phase=UPCOMING&size=3'),
      api<{ unreadCount: number }>('/notifications/me/unread-count'),
    ])
    return { upcoming, enrolled, unread }
  }, [])
  const upcoming = data.data?.upcoming.items ?? []
  const recommended = upcoming.filter((item) => item.remainingSeats > 0).slice(0, 3)
  const popular = upcoming.filter((item) => item.capacity > item.remainingSeats)
    .sort((a, b) => (b.capacity - b.remainingSeats) - (a.capacity - a.remainingSeats)).slice(0, 3)
  const today = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())
  return <>
    <PageTitle title={`你好，${user?.displayName ?? ''}`} description="看看校园里有哪些值得参加的活动。">
      <span className="date-display"><CalendarDays size={18} />{today}</span>
    </PageTitle>
    <section className="campus-hero">
      <div className="campus-hero-content"><span className="hero-topic"><Compass size={16} />探索校园</span>
        <h2>在校园，<br />遇见更多可能。</h2>
        <p>从一次活动开始，认识新伙伴。</p>
        <Link to={`${BASE}/activities`} className="hero-link">浏览活动 <ArrowRight size={17} /></Link>
      </div>
    </section>
    <QueryState {...data} retry={data.reload}>
      {data.data && <>
        <div className="summary-strip">
          <Link to={`${BASE}/activities?phase=UPCOMING`}><span className="summary-icon blue"><CalendarDays size={21} /></span>
            <span><small>即将开始</small><strong>{data.data.upcoming.totalElements}<small> 场活动</small></strong></span><ChevronRight size={17} /></Link>
          <Link to={`${BASE}/enrollments?view=upcoming`}><span className="summary-icon purple"><Ticket size={21} /></span>
            <span><small>我的待参加</small><strong>{data.data.enrolled.totalElements}<small> 场活动</small></strong></span><ChevronRight size={17} /></Link>
          <Link to={`${BASE}/notifications`}><span className="summary-icon orange"><Bell size={21} /></span>
            <span><small>未读通知</small><strong>{data.data.unread.unreadCount}<small> 条通知</small></strong></span><ChevronRight size={17} /></Link>
        </div>
        <section className="dashboard-section">
          <div className="section-title"><h2>推荐活动</h2>
            <Link to={`${BASE}/activities?phase=UPCOMING`}>查看全部 <ArrowRight size={15} /></Link></div>
          {recommended.length
            ? <div className="activity-grid">{recommended.map((activity) => <ActivityCard key={activity.id} activity={activity} />)}</div>
            : <QueryState loading={false} error="" empty="近期暂无可报名的活动。">{null}</QueryState>}
        </section>
        <div className="dashboard-bottom">
          <section className="panel mini-list"><h2>近期热门</h2>
            {popular.length ? popular.map((activity) => <Link key={activity.id} to={`${BASE}/activities/${activity.id}`}>
              <span>{activity.title}</span><small>{activity.capacity - activity.remainingSeats} 人已报名</small><ChevronRight size={15} />
            </Link>) : <p className="muted">近期活动还没有报名记录。</p>}
          </section>
          <section className="panel mini-list"><h2>我的行程</h2>
            {data.data.enrolled.items.length ? data.data.enrolled.items.map((entry) => <Link key={entry.id} to={`${BASE}/enrollments/${entry.id}`}>
              <span>{entry.activityTitle}</span><small>{dateTime(entry.startsAt)}</small><ChevronRight size={15} />
            </Link>) : <p className="muted">报名后，活动会出现在这里。</p>}
          </section>
        </div>
      </>}
    </QueryState>
  </>
}

const initialFilters = { keyword: '', category: '', phase: '', startsFrom: '', startsBefore: '' }

function Discovery() {
  const initialPhase = new URLSearchParams(window.location.search).get('phase')
  const [draft, setDraft] = useState({ ...initialFilters, phase: initialPhase === 'UPCOMING' ? initialPhase : '' })
  const [filters, setFilters] = useState(draft)
  const [page, setPage] = useState(0)
  const [formError, setFormError] = useState('')
  const query = useQuery(() => api<Page<Activity>>(`/activities${queryString({
    ...filters,
    startsFrom: filters.startsFrom ? new Date(filters.startsFrom).toISOString() : undefined,
    startsBefore: filters.startsBefore ? new Date(filters.startsBefore).toISOString() : undefined,
    page, size: 9,
  })}`), [filters, page])

  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFocus({ ...draft, keyword: draft.keyword.trim() })
  }

  function setFocus(values: typeof initialFilters) {
    setDraft(values)
    if (values.startsFrom && values.startsBefore && values.startsFrom >= values.startsBefore) {
      setFormError('截止时间需要晚于起始时间。')
      return
    }
    setFormError('')
    setPage(0)
    setFilters(values)
  }

  return <>
    <PageTitle title="活动广场" description="按兴趣或时间找到下一场校园活动。" />
    <form className="panel discovery-filter" onSubmit={apply}>
      <div className="discovery-search">
        <label className="search-box"><Search size={19} /><input aria-label="搜索活动" maxLength={120}
          placeholder="搜索名称、地点或活动介绍" value={draft.keyword} onChange={(event) => setDraft({ ...draft, keyword: event.target.value })} /></label>
        <button className="btn primary" type="submit">搜索</button>
      </div>
      <div className="filter-fields">
        <span className="filter-caption"><SlidersHorizontal size={17} />筛选</span>
        <label className="field">活动分类<select value={draft.category} onChange={(e) => setFocus({ ...draft, category: e.target.value })}>
          <option value="">全部分类</option>{Object.entries(categories).map(([key, label]) => <option value={key} key={key}>{label}</option>)}
        </select></label>
        <label className="field">活动状态<select value={draft.phase} onChange={(e) => setFocus({ ...draft, phase: e.target.value })}>
          <option value="">全部状态</option>{Object.entries(phases).map(([key, label]) => <option value={key} key={key}>{label}</option>)}
        </select></label>
        <label className="field">起始时间<input type="datetime-local" value={draft.startsFrom} onChange={(e) => setDraft({ ...draft, startsFrom: e.target.value })} /></label>
        <label className="field">截止时间<input type="datetime-local" value={draft.startsBefore} onChange={(e) => setDraft({ ...draft, startsBefore: e.target.value })} /></label>
        <button className="btn" type="button" onClick={() => setFocus({ ...initialFilters })}>清除筛选</button>
      </div>
      {formError && <p className="form-error" role="alert">{formError}</p>}
    </form>
    <div className="section-title search-heading"><h2>活动列表</h2><span className="muted">按开始时间排序</span></div>
    <QueryState {...query} empty={query.data?.items.length === 0 ? '没有符合条件的活动。试试其他关键词或清除筛选。' : undefined} retry={query.reload}>
      {query.data && <><div className="activity-grid">{query.data.items.map((activity) => <ActivityCard key={activity.id} activity={activity} />)}</div>
        <Pagination result={query.data} change={setPage} /></>}
    </QueryState>
  </>
}

function ActivityDetail() {
  const id = Number(useParams().id)
  const notify = useToast()
  const activity = useQuery(() => api<Activity>(`/activities/${id}`), [id])
  const mine = useQuery(async () => {
    try { return await api<Enrollment>(`/activities/${id}/enrollments/me`) }
    catch (error) { if (error instanceof ApiError && error.status === 404) return null; throw error }
  }, [id])
  const [action, setAction] = useState<'enroll' | 'cancel' | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const item = activity.data
  const enrolled = mine.data?.status === 'ENROLLED'
  const upcoming = item?.phase === 'UPCOMING'
  async function submit() {
    if (!action || busy) return
    setError('')
    setBusy(true)
    try {
      await api<Enrollment>(`/activities/${id}/enrollments${action === 'cancel' ? '/me' : ''}`,
        { method: action === 'cancel' ? 'DELETE' : 'POST' })
      notify(action === 'cancel' ? '已取消报名' : '报名成功')
      window.dispatchEvent(new Event('campusflow:notifications'))
      setAction(null)
      mine.reload()
      activity.reload()
    } catch (failure) {
      setError(errorText(failure))
      activity.reload()
    } finally {
      setBusy(false)
    }
  }

  return <>
    <Link className="back-link" to={`${BASE}/activities`}><ArrowLeft size={17} /> 返回活动广场</Link>
    <QueryState {...activity} retry={activity.reload}>
      {item && <article data-testid="activity-detail">
        <div className="detail-cover" style={{ backgroundImage: `linear-gradient(90deg, #101d36e0, #101d3628), url("${categoryImages[item.category]}")` }}>
          <span className="tag">{categories[item.category]}</span><h1>{item.title}</h1><p><MapPin size={18} />{item.location}</p>
        </div>
        <div className="detail-columns">
          <section className="panel detail-description"><h2>活动介绍</h2><p>{item.description}</p></section>
          <section className="panel detail-registration"><h2>活动信息</h2>
            <dl className="detail-facts">
              <div><dt><Calendar size={17} />开始</dt><dd>{dateTime(item.startsAt)}</dd></div>
              <div><dt><Clock3 size={17} />结束</dt><dd>{dateTime(item.endsAt)}</dd></div>
              <div><dt><UsersRound size={17} />当前名额</dt><dd>{item.capacity - item.remainingSeats} / {item.capacity} 人</dd></div>
              <div><dt>活动状态</dt><dd>{phases[item.phase]}</dd></div>
            </dl>
            <QueryState {...mine} retry={mine.reload}>
              <div className="registration-status"><span>我的报名</span><span className="tag">{enrolled ? '已报名' : mine.data?.status === 'CANCELLED' ? '已取消' : '未报名'}</span></div>
              {enrolled
                ? <button className="btn" disabled={!upcoming} onClick={() => { setError(''); setAction('cancel') }}>取消报名</button>
                : <button className="btn primary" disabled={!upcoming || item.remainingSeats === 0}
                  onClick={() => { setError(''); setAction('enroll') }}>立即报名</button>}
            </QueryState>
          </section>
        </div>
      </article>}
    </QueryState>
    {action && <ConfirmDialog title={action === 'cancel' ? '取消活动报名' : '确认参加活动'}
      text={action === 'cancel' ? '取消后，名额将开放给其他同学。' : `报名参加「${item?.title ?? ''}」？`}
      action={action === 'cancel' ? '确认取消' : '确认报名'} busy={busy} error={error} danger={action === 'cancel'}
      onClose={() => setAction(null)} onConfirm={submit} />}
  </>
}

const enrollmentTabs = [
  { key: 'all', label: '全部', status: undefined, phase: undefined },
  { key: 'upcoming', label: '待参加', status: 'ENROLLED', phase: 'UPCOMING' },
  { key: 'ongoing', label: '进行中', status: 'ENROLLED', phase: 'ONGOING' },
  { key: 'ended', label: '已结束', status: 'ENROLLED', phase: 'ENDED' },
  { key: 'cancelled', label: '已取消', status: 'CANCELLED', phase: undefined },
]

function MyActivities() {
  const selected = new URLSearchParams(window.location.search).get('view')
  const [tab, setTab] = useState(enrollmentTabs.find((item) => item.key === selected) ?? enrollmentTabs[0])
  const [page, setPage] = useState(0)
  const query = useQuery(() => api<Page<MyEnrollment>>(`/enrollments/me${queryString({
    status: tab.status, phase: tab.phase, page,
  })}`), [tab, page])
  return <><PageTitle title="我的活动" description="查看报名记录和活动时间。" />
    <div className="tabs" aria-label="报名状态">{enrollmentTabs.map((item) => <button type="button" key={item.key}
      className={tab === item ? 'active' : ''} aria-pressed={tab === item} onClick={() => { setTab(item); setPage(0) }}>{item.label}</button>)}</div>
    <QueryState {...query} empty={query.data?.items.length === 0 ? '此状态下还没有活动。' : undefined} retry={query.reload}>
      {query.data && <><div className="panel record-list">{query.data.items.map((entry) => <Link key={entry.id}
        to={`${BASE}/enrollments/${entry.id}`} className="record-row" data-testid="enrollment-row">
        <span className="summary-icon blue"><Ticket size={20} /></span>
        <span><strong>{entry.activityTitle}</strong><small>{dateTime(entry.startsAt)}</small></span>
        <span className={`tag ${entry.status === 'CANCELLED' ? 'tag-muted' : 'tag-green'}`}>{entry.status === 'CANCELLED' ? '已取消' : '已报名'}</span><ChevronRight size={18} />
      </Link>)}</div><Pagination result={query.data} change={setPage} /></>}
    </QueryState>
  </>
}

function EnrollmentDetails() {
  const id = Number(useParams().id)
  const data = useQuery(() => api<MyEnrollment>(`/enrollments/me/${id}`), [id])
  return <><Link className="back-link" to={`${BASE}/enrollments`}><ArrowLeft size={17} /> 返回我的活动</Link>
    <PageTitle title="报名详情" />
    <QueryState {...data} retry={data.reload}>
      {data.data && <section className="panel detail-description"><h2>{data.data.activityTitle}</h2>
        <dl className="detail-facts">
          <div><dt>报名状态</dt><dd>{data.data.status === 'ENROLLED' ? '已报名' : '已取消'}</dd></div>
          <div><dt>报名时间</dt><dd>{dateTime(data.data.enrolledAt)}</dd></div>
          {data.data.cancelledAt && <div><dt>取消时间</dt><dd>{dateTime(data.data.cancelledAt)}</dd></div>}
          <div><dt>开始时间</dt><dd>{dateTime(data.data.startsAt)}</dd></div>
          <div><dt>结束时间</dt><dd>{dateTime(data.data.endsAt)}</dd></div>
        </dl>
        {data.data.activityStatus === 'PUBLISHED' && <Link className="btn primary" to={`${BASE}/activities/${data.data.activityId}`}>查看活动</Link>}
      </section>}
    </QueryState>
  </>
}

function NotificationDetails({ id, close, change }: { id: number; close: () => void; change: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null)
  const notice = useQuery(() => api<Notice>(`/notifications/${id}`), [id])
  const notify = useToast()
  const [busy, setBusy] = useState(false)
  useEffect(() => { dialog.current?.showModal() }, [])
  async function read() {
    setBusy(true)
    try {
      await api<Notice>(`/notifications/${id}/read`, { method: 'PATCH' })
      notify('已标记为已读')
      change()
      notice.reload()
    } catch (failure) { notify(errorText(failure), 'error') }
    finally { setBusy(false) }
  }
  return <dialog className="dialog" ref={dialog} aria-labelledby="notice-heading"
    onCancel={(event) => { event.preventDefault(); close() }}>
    <div className="section-title"><h2 id="notice-heading">通知详情</h2><button className="icon-button" aria-label="关闭通知" onClick={close}><X size={19} /></button></div>
    <QueryState {...notice} retry={notice.reload}>
      {notice.data && <><p>{notice.data.message}</p><p className="muted">{dateTime(notice.data.createdAt)}</p>
        <div className="dialog-actions">
          {notice.data.type.startsWith('ENROLLMENT_') && <Link className="btn" to={`${BASE}/activities/${notice.data.activityId}`}>查看活动</Link>}
          {!notice.data.readAt && <button className="btn primary" disabled={busy} onClick={read}>标记已读</button>}
          {notice.data.readAt && <span className="tag tag-green">已读</span>}
        </div></>}
    </QueryState>
  </dialog>
}

function Notifications() {
  const notify = useToast()
  const [page, setPage] = useState(0)
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [selected, setSelected] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const [error, setError] = useState('')
  const notices = useQuery(() => api<Page<Notice>>(`/notifications/me${queryString({ page, unreadOnly })}`), [page, unreadOnly])
  const unread = useQuery(() => api<{ unreadCount: number }>('/notifications/me/unread-count'), [])
  function updated() {
    if (page && unreadOnly && notices.data?.items.length === 1) setPage(page - 1)
    else notices.reload()
    unread.reload()
    window.dispatchEvent(new Event('campusflow:notifications'))
  }
  async function readAll() {
    setBusy(true)
    setError('')
    try {
      const result = await api<{ updatedCount: number }>('/notifications/me/read-all', { method: 'PATCH' })
      notify(`已将 ${result.updatedCount} 条通知标记为已读`)
      setConfirm(false)
      setPage(0)
      updated()
    } catch (failure) { setError(errorText(failure)) }
    finally { setBusy(false) }
  }
  return <><PageTitle title="通知中心" description={unread.data ? `${unread.data.unreadCount} 条未读通知` : undefined}>
    <button className="btn" disabled={!unread.data?.unreadCount} onClick={() => setConfirm(true)}><Check size={18} />全部已读</button>
  </PageTitle>
    <div className="tabs" aria-label="通知状态">
      <button type="button" className={!unreadOnly ? 'active' : ''} aria-pressed={!unreadOnly} onClick={() => { setUnreadOnly(false); setPage(0) }}>全部通知</button>
      <button type="button" className={unreadOnly ? 'active' : ''} aria-pressed={unreadOnly} onClick={() => { setUnreadOnly(true); setPage(0) }}>仅未读</button>
    </div>
    <QueryState {...notices} retry={notices.reload} empty={notices.data?.items.length === 0 ? '没有新的通知。' : undefined}>
      {notices.data && <><div className="panel record-list">{notices.data.items.map((notice) => <button key={notice.id}
        className="record-row notice-row" data-testid="notification-row" onClick={() => setSelected(notice.id)}>
        <span className={`summary-icon ${notice.readAt ? 'neutral' : 'blue'}`}><Bell size={19} /></span>
        <span><strong>{notice.message}</strong><small>{dateTime(notice.createdAt)}</small></span>
        <span className={`tag ${notice.readAt ? 'tag-muted' : ''}`}>{notice.readAt ? '已读' : '未读'}</span><ChevronRight size={18} />
      </button>)}</div><Pagination result={notices.data} change={setPage} /></>}
    </QueryState>
    {selected !== null && <NotificationDetails key={selected} id={selected} close={() => setSelected(null)} change={updated} />}
    {confirm && <ConfirmDialog title="全部标记为已读" text="将当前账号的所有未读通知标记为已读？" action="确认全部已读"
      onClose={() => setConfirm(false)} onConfirm={readAll} busy={busy} error={error} />}
  </>
}

function Profile() {
  const { user, setUser } = useAuth()
  const notify = useToast()
  const [name, setName] = useState(user?.displayName ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    if (!name.trim()) { setError('请输入显示名称。'); return }
    setError('')
    setBusy(true)
    try {
      const saved = await api<User>('/users/me', { method: 'PATCH', body: JSON.stringify({ displayName: name.trim() }) })
      setUser(saved)
      setName(saved.displayName)
      notify('个人资料已更新')
    } catch (failure) { setError(errorText(failure)) }
    finally { setBusy(false) }
  }
  return <><PageTitle title="个人中心" description="查看账号状态及更新显示名称。" />
    <section className="panel profile-card">
      <div className="profile-card-head"><span className="avatar">{user?.displayName.slice(0, 1)}</span>
        <div><h2>{user?.displayName}</h2><p>@{user?.username}</p></div><span className="tag tag-green">{user?.status === 'ACTIVE' ? '正常' : '已禁用'}</span>
      </div>
      <form onSubmit={save}>
        <label className="field">显示名称<input autoComplete="nickname" required maxLength={80} value={name} onChange={(e) => setName(e.target.value)} /></label>
        {error && <p className="form-error" role="alert">{error}</p>}
        <button className="btn primary" disabled={busy || name.trim() === user?.displayName}>保存资料</button>
      </form>
    </section>
  </>
}

export default function StudentPages() {
  return <Routes>
    <Route index element={<Dashboard />} />
    <Route path="activities" element={<Discovery />} />
    <Route path="activities/:id" element={<ActivityDetail />} />
    <Route path="enrollments" element={<MyActivities />} />
    <Route path="enrollments/:id" element={<EnrollmentDetails />} />
    <Route path="notifications" element={<Notifications />} />
    <Route path="profile" element={<Profile />} />
    <Route path="*" element={<div className="empty"><BookOpen size={24} /><p>找不到这个页面。</p><Link to={BASE}>返回首页</Link></div>} />
  </Routes>
}
