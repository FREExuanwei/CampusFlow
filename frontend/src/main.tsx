import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Link, NavLink, Navigate, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import {
  ArrowLeft, ArrowRight, Bell, CalendarDays, ChevronDown, ChevronRight, ClipboardCheck, Compass,
  Eye, EyeOff, GraduationCap, House, LayoutDashboard, ListChecks, LogOut, Menu, ShieldCheck,
  Ticket, UserRound, UsersRound, X,
} from 'lucide-react'
import { api, AppProviders, errorText, useAuth, useQuery, type Role, type User } from './core'
import StudentPages from './student/StudentPages'
import AdminPages from './admin/AdminPages'
import './styles.css'
import authStyles from './auth.module.css'

const studentNavigation = [
  { to: '/student', label: '首页', icon: House, end: true },
  { to: '/student/activities', label: '活动广场', icon: Compass },
  { to: '/student/enrollments', label: '我的活动', icon: Ticket },
  { to: '/student/notifications', label: '通知中心', icon: Bell },
  { to: '/student/profile', label: '个人中心', icon: UserRound },
]
const adminNavigation = [
  { to: '/admin', label: '管理概览', icon: LayoutDashboard, end: true },
  { to: '/admin/activities', label: '活动管理', icon: CalendarDays },
  { to: '/admin/review', label: '活动审核', icon: ClipboardCheck },
  { to: '/admin/users', label: '用户管理', icon: UsersRound },
  { to: '/admin/audit', label: '操作审计', icon: ListChecks },
]

function homePath(user: User) {
  return user.roles.includes('ADMIN') ? '/admin' : '/student'
}

function managementTitle(path: string, fallback: string) {
  if (path === '/admin/activities/new') return '新建活动'
  if (path.endsWith('/enrollments')) return '活动报名'
  if (path.endsWith('/edit')) return '编辑活动'
  if (/^\/admin\/activities\/\d+$/.test(path)) return '活动详情'
  if (/^\/admin\/users\/\d+$/.test(path)) return '用户详情'
  return fallback
}

function Brand({ light = false }: { light?: boolean }) {
  return <div className={`brand ${light ? 'brand-light' : ''}`}>
    <span className="brand-mark"><GraduationCap size={25} strokeWidth={1.9} /></span>
    <span>Campus<span className="brand-accent">Flow</span><small>校园活动中心</small></span>
  </div>
}

function AuthenticationPage({ register = false }: { register?: boolean }) {
  const { login, reason } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [showPassword, setShowPassword] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const notice = !register && new URLSearchParams(location.search).has('registered') ? '注册成功，请登录' : reason

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    const values = new FormData(event.currentTarget)
    const username = String(values.get('username') ?? '').trim()
    const password = String(values.get('password') ?? '')
    const displayName = String(values.get('displayName') ?? '').trim()
    setError('')
    if (new TextEncoder().encode(password).length > 72) {
      setError('密码长度不能超过 72 字节。')
      return
    }
    const characters = Array.from(password).length
    if (register && (characters < 12 || characters > 64 || !password.trim() || password.includes('\0'))) {
      setError('请输入 12–64 个字符的密码，最多 72 个 UTF-8 字节。')
      return
    }
    if (register && !displayName) {
      setError('请输入显示名称。')
      return
    }
    setBusy(true)
    try {
      if (register) {
        await api<User>('/auth/register', { method: 'POST', body: JSON.stringify({ username, password, displayName }) })
        navigate('/login?registered=1', { replace: true })
      } else {
        const user = await login(username, password)
        navigate(homePath(user), { replace: true })
      }
    } catch (failure) {
      setError(errorText(failure))
    } finally {
      setBusy(false)
    }
  }

  return <main className={authStyles['auth-layout']}>
    <section className={authStyles['auth-content']}>
      <Brand />
      <div className={authStyles['auth-form']}>
        {register && <Link className="back-link" to="/login"><ArrowLeft size={16} /> 返回登录</Link>}
        <h1>{register ? '创建学生账号' : '欢迎回到校园'}</h1>
        <p>{register ? '填写基本资料，开始参加校园活动。' : '登录 CampusFlow，查看活动和报名。'}</p>
        {notice && <p className={authStyles['auth-notice']} role="status">{notice}</p>}
        <form onSubmit={submit}>
          {register && <label className="field">显示名称<input name="displayName" maxLength={80} autoComplete="nickname" required placeholder="你的校园昵称" /></label>}
          <label className="field">用户名<input name="username" required minLength={3} maxLength={32}
            pattern="[A-Za-z0-9_]{3,32}" title="3–32 个英文字母、数字或下划线" autoComplete="username" placeholder="请输入用户名" /></label>
          <label className="field">密码
            <span className={authStyles['password-field']}>
              <input name="password" type={showPassword ? 'text' : 'password'} required minLength={register ? 12 : 1}
                maxLength={128} autoComplete={register ? 'new-password' : 'current-password'} placeholder={register ? '至少 12 个字符' : '请输入密码'} />
              <button type="button" className="icon-button" aria-label={showPassword ? '隐藏密码' : '显示密码'} onClick={() => setShowPassword(!showPassword)}>
                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </span>
          </label>
          {error && <p className="form-error" role="alert">{error}</p>}
          <button type="submit" className={`btn primary ${authStyles['auth-submit']}`} disabled={busy}>{busy ? '处理中…' : register ? '注册' : '登录'}<ArrowRight size={18} /></button>
        </form>
        <p className={authStyles['auth-switch']}>{register ? '已有账号？' : '还没有账号？'} <Link to={register ? '/login' : '/register'}>{register ? '登录' : '注册学生账号'}</Link></p>
      </div>
      <span className={authStyles['auth-foot']}><ShieldCheck size={16} /> 本机演示 · 校园活动管理</span>
    </section>
    <aside className={authStyles['auth-visual']} aria-label="校园环境">
      <div><h2>校园里的下一场相遇。</h2><p>发现有趣的活动，也找到和你一样好奇的人。</p></div>
    </aside>
  </main>
}

function SignInRoute({ register }: { register?: boolean }) {
  const { user } = useAuth()
  return user ? <Navigate to={homePath(user)} replace /> : <AuthenticationPage key={register ? 'register' : 'login'} register={register} />
}

function Guard({ role, children }: { role: Role; children: ReactNode }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  if (!user.roles.includes(role)) {
    return <main className="access-screen"><ShieldCheck size={32} /><h1>没有访问权限</h1>
      <p>此页面仅面向{role === 'ADMIN' ? '管理员' : '学生'}开放。</p>
      <Link className="btn primary" to={homePath(user)}>返回工作台</Link>
    </main>
  }
  return children
}

function AppFrame({ admin }: { admin: boolean }) {
  const { user, logout } = useAuth()
  const location = useLocation()
  const [menu, setMenu] = useState(false)
  const [profileOpen, setProfileOpen] = useState(false)
  const nav = admin ? adminNavigation : studentNavigation
  const active = [...nav].reverse().find((item) => location.pathname === item.to || location.pathname.startsWith(`${item.to}/`))
  const unread = useQuery(() => api<{ unreadCount: number }>('/notifications/me/unread-count'), [location.pathname])
  useEffect(() => { setMenu(false); setProfileOpen(false) }, [location.pathname])
  useEffect(() => {
    const refresh = () => unread.reload()
    window.addEventListener('campusflow:notifications', refresh)
    return () => window.removeEventListener('campusflow:notifications', refresh)
  }, [unread.reload])

  if (!user) return null
  return <div className="shell">
    <a className="skip-link" href="#main-content">跳转到主内容</a>
    {menu && <button className="nav-overlay" aria-label="关闭导航" onClick={() => setMenu(false)} />}
    <aside id="workspace-navigation" className={`sidebar ${menu ? 'sidebar-open' : ''}`} aria-label={admin ? '管理员导航' : '学生导航'}>
      <Link className="brand-link" to={admin ? '/admin' : '/student'} aria-label="CampusFlow 首页"><Brand light /></Link>
      <div className="workspace-label"><span />{admin ? '管理工作台' : '学生工作台'}</div>
      <nav className="sidebar-nav">
        {nav.map(({ to, label, icon: Icon, end }) => <NavLink key={to} to={to} end={end} onClick={() => setMenu(false)}>
          <Icon size={19} /> <span>{label}</span>
          {!admin && label === '通知中心' && !!unread.data?.unreadCount && <b className="nav-count">{unread.data.unreadCount}</b>}
        </NavLink>)}
      </nav>
      <div className="sidebar-foot">
        <div className="sidebar-identity"><span className="avatar">{user.displayName.slice(0, 1)}</span>
          <span><strong>{user.displayName}</strong><small>{admin ? '管理员' : '学生'}</small></span>
        </div>
        <button className="icon-button" aria-label="退出登录" onClick={logout}><LogOut size={19} /></button>
      </div>
    </aside>
    <div className="workspace">
      <header className="topbar">
        <button className="icon-button mobile-menu" aria-label={menu ? '关闭导航菜单' : '打开导航菜单'}
          aria-expanded={menu} aria-controls="workspace-navigation"
          onClick={() => setMenu(!menu)}>{menu ? <X size={23} /> : <Menu size={23} />}</button>
        <div className="breadcrumbs">{admin ? '管理端' : '校园门户'} <ChevronRight size={15} /> <span>{active?.label ?? '工作台'}</span></div>
        <div className="top-actions">
          {!admin && <Link aria-label="打开通知中心" className="icon-button top-bell" to="/student/notifications">
            <Bell size={21} />{!!unread.data?.unreadCount && <i aria-hidden="true" />}
          </Link>}
          <div className="profile-menu">
            <button className="profile-trigger" aria-label="打开账号菜单" aria-expanded={profileOpen} onClick={() => setProfileOpen(!profileOpen)}>
              <span className="avatar">{user.displayName.slice(0, 1)}</span><span>{user.displayName}</span><ChevronDown size={15} />
            </button>
            {profileOpen && <div className="profile-popover">
              {!admin && <Link to="/student/profile" onClick={() => setProfileOpen(false)}><UserRound size={17} />个人中心</Link>}
              {user.roles.length > 1 && <Link to={admin ? '/student' : '/admin'} onClick={() => setProfileOpen(false)}>
                <ArrowRight size={17} />{admin ? '切换到学生端' : '切换到管理端'}
              </Link>}
              <button onClick={logout}><LogOut size={17} />退出登录</button>
            </div>}
          </div>
        </div>
      </header>
      <main className="workspace-main" id="main-content">
        {admin && <div className="page-title"><h1>{managementTitle(location.pathname, active?.label ?? '管理概览')}</h1></div>}
        <Outlet />
      </main>
    </div>
  </div>
}

function App() {
  return <BrowserRouter><AppProviders><Routes>
    <Route path="/" element={<Navigate to="/login" replace />} />
    <Route path="/login" element={<SignInRoute />} />
    <Route path="/register" element={<SignInRoute register />} />
    <Route path="/student/*" element={<Guard role="STUDENT"><AppFrame admin={false} /></Guard>}>
      <Route path="*" element={<StudentPages />} />
    </Route>
    <Route path="/admin/*" element={<Guard role="ADMIN"><AppFrame admin /></Guard>}>
      <Route path="*" element={<AdminPages />} />
    </Route>
    <Route path="*" element={<main className="access-screen"><h1>页面不存在</h1><Link className="btn primary" to="/">返回首页</Link></main>} />
  </Routes></AppProviders></BrowserRouter>
}

createRoot(document.getElementById('root')!).render(<App />)
