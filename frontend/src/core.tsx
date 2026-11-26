import {
  createContext, useCallback, useContext, useEffect, useRef, useState,
  type DependencyList, type ReactNode,
} from 'react'
import { AlertCircle, Check, ChevronLeft, ChevronRight, Inbox, LoaderCircle, X } from 'lucide-react'

export type Role = 'STUDENT' | 'ADMIN'
export type User = { id: number; username: string; displayName: string; roles: Role[]; status: 'ACTIVE' | 'DISABLED'; createdAt: string }
export type Page<T> = { items: T[]; page: number; size: number; totalElements: number; totalPages: number }
export type Category = 'GENERAL' | 'ACADEMIC' | 'CULTURE' | 'SPORTS' | 'VOLUNTEER'
export type Phase = 'UPCOMING' | 'ONGOING' | 'ENDED'
export type Activity = {
  id: number; title: string; description: string; location: string; startsAt: string;
  endsAt: string; capacity: number; category: Category; phase: Phase; remainingSeats: number;
}
export type Enrollment = {
  id: number; activityId: number; status: 'ENROLLED' | 'CANCELLED'; enrolledAt: string; cancelledAt: string | null;
}
export type MyEnrollment = Enrollment & { activityTitle: string; activityStatus: string; startsAt: string; endsAt: string }
export type Notice = { id: number; activityId: number; type: string; message: string; createdAt: string; readAt: string | null }

export const categories: Record<Category, string> = {
  GENERAL: '校园生活', ACADEMIC: '学术科技', CULTURE: '文化艺术', SPORTS: '体育运动', VOLUNTEER: '志愿服务',
}
export const phases: Record<Phase, string> = { UPCOMING: '即将开始', ONGOING: '进行中', ENDED: '已结束' }

type Envelope<T> = { success: boolean; code: string; message: string; data: T }
type LoginResponse = { accessToken: string; tokenType: string; expiresIn: number }
const invalidSessionEvent = 'campusflow:invalid-session'
let sessionToken: string | undefined

export class ApiError extends Error {
  constructor(readonly status: number, readonly code: string, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = sessionToken
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body != null) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  let response: Response
  try {
    response = await fetch(`/api/v1${path}`, { ...init, headers, cache: 'no-store', redirect: 'error' })
  } catch (failure) {
    if (failure instanceof DOMException && failure.name === 'AbortError') throw failure
    throw new ApiError(0, 'NETWORK', '暂时无法连接服务，请确认演示系统已启动后重试。')
  }
  if (response.status === 401 && token && sessionToken === token) {
    sessionToken = undefined
    window.dispatchEvent(new Event(invalidSessionEvent))
  }
  const envelope = await response.json().catch(() => null) as Envelope<T> | null
  if (!response.ok || envelope?.success !== true) {
    const code = envelope?.code || 'UNEXPECTED_RESPONSE'
    const message = response.status === 403 ? '你没有权限执行此操作。'
      : response.status === 401 && token ? '会话已失效，请重新登录'
        : typeof envelope?.message === 'string' ? envelope.message
          : '服务未能完成请求，请稍后重试。'
    throw new ApiError(response.status, code, message)
  }
  return envelope.data
}

export function queryString(values: Record<string, string | number | boolean | null | undefined>) {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined && value !== null && value !== '') query.set(key, String(value))
  }
  return query.size ? `?${query}` : ''
}

export function errorText(error: unknown) {
  return error instanceof ApiError ? error.message : '操作未完成，请稍后重试。'
}

export function useQuery<T>(loader: () => Promise<T>, deps: DependencyList) {
  const [data, setData] = useState<T>()
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [revision, setRevision] = useState(0)
  const reload = useCallback(() => setRevision((value) => value + 1), [])
  useEffect(() => {
    let current = true
    setLoading(true)
    setError('')
    Promise.resolve().then(loader).then((value) => { if (current) setData(value) })
      .catch((failure: unknown) => { if (current) setError(errorText(failure)) })
      .finally(() => { if (current) setLoading(false) })
    return () => { current = false }
  }, [...deps, revision])
  return { data, error, loading, reload }
}

type AuthContextValue = {
  user: User | null; reason: string; login: (username: string, password: string) => Promise<User>;
  logout: () => void; setUser: (user: User) => void;
}
const AuthContext = createContext<AuthContextValue | null>(null)
const ToastContext = createContext<(message: string, tone?: 'success' | 'error') => void>(() => {})

export function AppProviders({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [reason, setReason] = useState('')
  const [toast, setToast] = useState<{ message: string; tone: 'success' | 'error' } | null>(null)
  const expiration = useRef<number | undefined>(undefined)
  const toastTimer = useRef<number | undefined>(undefined)
  const endSession = useCallback((message = '') => {
    sessionToken = undefined
    window.clearTimeout(expiration.current)
    setUser(null)
    setReason(message)
    setToast(null)
  }, [])

  useEffect(() => {
    const expired = () => endSession('会话已失效，请重新登录')
    window.addEventListener(invalidSessionEvent, expired)
    return () => {
      window.removeEventListener(invalidSessionEvent, expired)
      window.clearTimeout(expiration.current)
      window.clearTimeout(toastTimer.current)
    }
  }, [endSession])

  async function login(username: string, password: string) {
    const token = await api<LoginResponse>('/auth/login', {
      method: 'POST', body: JSON.stringify({ username, password }),
    })
    sessionToken = token.accessToken
    try {
      const profile = await api<User>('/users/me')
      setUser(profile)
      setReason('')
      window.clearTimeout(expiration.current)
      expiration.current = window.setTimeout(() => endSession('会话已失效，请重新登录'), token.expiresIn * 1000)
      return profile
    } catch (error) {
      sessionToken = undefined
      throw error
    }
  }

  const notify = useCallback((message: string, tone: 'success' | 'error' = 'success') => {
    setToast({ message, tone })
    window.clearTimeout(toastTimer.current)
    toastTimer.current = window.setTimeout(() => setToast(null), 5000)
  }, [])

  return <AuthContext.Provider value={{ user, reason, login, logout: () => endSession(), setUser }}>
    <ToastContext.Provider value={notify}>
      {children}
      {toast && <div className={`toast toast-${toast.tone}`} role={toast.tone === 'error' ? 'alert' : 'status'}>
        {toast.tone === 'success' ? <Check size={18} /> : <AlertCircle size={18} />}
        <span>{toast.message}</span>
        <button className="icon-button" aria-label="关闭提示" onClick={() => setToast(null)}><X size={17} /></button>
      </div>}
    </ToastContext.Provider>
  </AuthContext.Provider>
}

export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('Authentication provider is missing')
  return value
}
export const useToast = () => useContext(ToastContext)

export function QueryState({ loading, error, empty, retry, children }: {
  loading: boolean; error: string; empty?: string; retry?: () => void; children: ReactNode
}) {
  if (loading) return <div className="empty" role="status"><LoaderCircle className="spinner" size={24} /> 正在加载</div>
  if (error) return <div className="empty" role="alert"><AlertCircle size={24} /><span>{error}</span>
    {retry && <button className="btn" onClick={retry}>重试</button>}
  </div>
  if (empty) return <div className="empty"><Inbox size={28} /><span>{empty}</span></div>
  return children
}

export function Pagination({ result, change }: { result: Page<unknown>; change: (page: number) => void }) {
  return <nav className="pagination" aria-label="分页">
    <span>共 {result.totalElements} 条</span>
    <div>
      <button className="icon-button" type="button" aria-label="上一页" disabled={result.page === 0} onClick={() => change(result.page - 1)}><ChevronLeft size={19} /></button>
      <span>第 {result.page + 1} / {Math.max(1, result.totalPages)} 页</span>
      <button className="icon-button" type="button" aria-label="下一页" disabled={result.page + 1 >= result.totalPages} onClick={() => change(result.page + 1)}><ChevronRight size={19} /></button>
    </div>
  </nav>
}

export function ConfirmDialog({ title, text, action, danger, onClose, onConfirm, busy, error }: {
  title: string; text: string; action: string; danger?: boolean; onClose: () => void;
  onConfirm: () => void; busy: boolean; error: string;
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => { dialog.current?.showModal() }, [])
  return <dialog className="dialog" ref={dialog} aria-labelledby="confirm-title"
    onCancel={(event) => { event.preventDefault(); if (!busy) onClose() }}>
    <h2 id="confirm-title">{title}</h2>
    <p>{text}</p>
    {error && <p className="form-error" role="alert">{error}</p>}
    <div className="dialog-actions">
      <button className="btn" autoFocus disabled={busy} onClick={onClose}>返回</button>
      <button className={`btn ${danger ? 'danger' : 'primary'}`} disabled={busy} onClick={onConfirm}>{busy ? '处理中…' : action}</button>
    </div>
  </dialog>
}

export function dateTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })
    .format(new Date(value))
}
