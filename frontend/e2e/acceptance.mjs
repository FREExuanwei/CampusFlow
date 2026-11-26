import { randomBytes } from 'node:crypto'
import { mkdir, readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const origin = 'http://127.0.0.1:5173'
const backend = 'http://127.0.0.1:18087'
const healthPath = '/api/v1/demo/health'
const demoDirectory = fileURLToPath(new URL('../../.campusflow-demo/', import.meta.url))
const screenshotDirectory = join(demoDirectory, 'acceptance')
const timeout = 10_000
const suffix = `${Date.now().toString(36)}_${randomBytes(3).toString('hex')}`
const registeredAccount = { username: `e2e_${suffix}`, password: randomBytes(24).toString('base64url') }
const wrongPassword = randomBytes(24).toString('base64url')
const secrets = new Set([registeredAccount.password, wrongPassword])

let stepName = '检查 H2 演示服务'
let browser

function check(condition, message) {
  if (!condition) throw new Error(message)
}

async function step(name, action) {
  stepName = name
  const result = await action()
  console.log(`通过 · ${name}`)
  return result
}

async function guardedHealth(base) {
  const response = await fetch(`${base}${healthPath}`, {
    redirect: 'error',
    signal: AbortSignal.timeout(timeout),
  })
  check(response.ok, `演示健康检查未就绪（HTTP ${response.status}）：${base}`)
  const envelope = await response.json().catch(() => null)
  check(envelope?.success && envelope.data?.status === 'UP' && envelope.data.authority === 'H2_DEMO',
    `未收到 H2 演示标记：${base}`)
}

async function readDemoAccounts() {
  let accounts
  try {
    const file = await readFile(join(demoDirectory, 'credentials.json'), 'utf8')
    accounts = JSON.parse(file.replace(/^\uFEFF/, '')).accounts
  } catch {
    throw new Error('无法读取演示账号，请先使用项目的本地演示启动脚本。')
  }
  const expected = { admin: 'demo_admin', student: 'demo_student', student2: 'demo_student2' }
  for (const [kind, username] of Object.entries(expected)) {
    const account = accounts?.[kind]
    check(account?.username === username && typeof account.password === 'string'
      && /^[A-Za-z0-9_+\-=/\.]{12,72}$/.test(account.password), `本地演示账号缺少有效字段（${kind}）`)
    secrets.add(account.password)
  }
  return accounts
}

async function openPage(options = {}) {
  const context = await browser.newContext({ baseURL: origin, serviceWorkers: 'block', ...options })
  await context.route(url => url.origin !== origin, route => route.abort())
  const page = await context.newPage()
  page.setDefaultTimeout(timeout)
  return page
}

function observe(page, method, path, query = () => true) {
  return page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === origin && url.pathname === `/api/v1${path}`
      && response.request().method() === method && query(url.searchParams)
  })
}

async function verifyResponse(response, accepted) {
  const status = response.status()
  check(accepted.includes(status), `API 返回 HTTP ${status}，预期 ${accepted.join(' 或 ')}`)
  let envelope
  try {
    envelope = await response.json()
  } catch {
    throw new Error('API 响应未使用 JSON 信封')
  }
  check(envelope.success === (status < 400), `HTTP ${status} 的 JSON 信封与状态不一致`)
  return envelope
}

async function fromControl(page, method, path, useControl, accepted = [200], query) {
  const [response] = await Promise.all([observe(page, method, path, query), useControl()])
  return (await verifyResponse(response, accepted)).data
}

async function api(page, token, method, path, statuses, data) {
  const response = await page.context().request.fetch(`/api/v1${path}`, {
    method,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    data,
    timeout,
    maxRedirects: 0,
  })
  try {
    return await verifyResponse(response, statuses)
  } finally {
    await response.dispose()
  }
}

function button(page, name) {
  return page.getByRole('button', { name, exact: true })
}

function nav(page, path) {
  return page.locator(`a[href="${path}"]`).filter({ visible: true }).first()
}

async function visible(locator) {
  await locator.waitFor({ state: 'visible' })
}

async function fillCredentials(page, username, password) {
  try {
    await page.getByLabel('用户名', { exact: true }).fill(username)
    await page.getByLabel('密码', { exact: true }).fill(password)
  } catch {
    throw new Error('无法填写认证表单，检查用户名和密码字段是否可见')
  }
}

async function login(page, account, landing) {
  await page.goto('/login')
  await fillCredentials(page, account.username, account.password)
  const result = await fromControl(page, 'POST', '/auth/login', () => button(page, '登录').click())
  check(typeof result?.accessToken === 'string' && result.accessToken.length > 0, '登录响应缺少访问令牌')
  secrets.add(result.accessToken)
  await page.waitForURL(`${origin}${landing}`)
  return result.accessToken
}

async function capture(page, name) {
  check(/^\/(student|admin)(\/|$)/.test(new URL(page.url()).pathname), '截图仅限已登录的业务页面')
  check(await page.locator('input[type="password"]').count() === 0, '认证表单禁止截图')
  await page.screenshot({ path: join(screenshotDirectory, `${name}.png`), animations: 'disabled' })
}

async function assertNoOverflow(page) {
  const fits = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)
  check(fits, '页面存在横向溢出')
}

function localDateTime(date) {
  const shifted = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return shifted.toISOString().slice(0, 16)
}

async function confirm(page, trigger, confirmation, method, path, fill) {
  await button(page, trigger).click()
  const dialog = page.getByRole('dialog')
  await visible(dialog)
  if (fill) await fill(dialog)
  const data = await fromControl(page, method, path, () => button(dialog, confirmation).click())
  await dialog.waitFor({ state: 'hidden' })
  return data
}

function expectForbid(envelope) {
  check(envelope.code === 'FORBIDDEN', '权限错误应使用 FORBIDDEN')
}

try {
  await step('检查 H2 演示服务和前端代理', () => Promise.all([guardedHealth(backend), guardedHealth(origin)]))
  const accounts = await readDemoAccounts()
  await mkdir(screenshotDirectory, { recursive: true })

  // Prevent debug modes from recording form values, HTTP headers, or browser protocol messages.
  delete process.env.DEBUG
  delete process.env.PWDEBUG
  const { chromium } = await import('playwright-core')
  browser = await chromium.launch({ channel: 'msedge', headless: true })

  await step('注册校验、登录反馈、资料修改和退出登录', async () => {
    const page = await openPage()
    try {
      await page.goto('/register')
      await fillCredentials(page, registeredAccount.username, '🏫'.repeat(6))
      await page.getByLabel('显示名称', { exact: true }).fill('浏览器验收学生')
      await button(page, '注册').click()
      await visible(page.getByRole('alert').filter({ hasText: '12–64' }))
      await fillCredentials(page, registeredAccount.username, '界'.repeat(25))
      await button(page, '注册').click()
      await visible(page.getByRole('alert').filter({ hasText: '72 字节' }))
      check(new URL(page.url()).pathname === '/register', '违反密码规则时应保留注册页')
      await fillCredentials(page, registeredAccount.username, registeredAccount.password)
      const created = await fromControl(page, 'POST', '/auth/register', () => button(page, '注册').click(), [201])
      check(created.roles?.length === 1 && created.roles[0] === 'STUDENT', '公开注册必须只分配学生角色')
      await page.waitForURL(url => url.pathname === '/login')
      await visible(page.getByText('注册成功，请登录', { exact: true }))
      await fillCredentials(page, registeredAccount.username, wrongPassword)
      await fromControl(page, 'POST', '/auth/login', () => button(page, '登录').click(), [401])
      await visible(page.getByText(/用户名或密码/).first())

      await login(page, registeredAccount, '/student')
      await nav(page, '/student/profile').click()
      await page.getByLabel('显示名称', { exact: true }).fill('验收学生新昵称')
      const changed = await fromControl(page, 'PATCH', '/users/me', () => button(page, '保存资料').click())
      check(changed.displayName === '验收学生新昵称' && changed.roles.includes('STUDENT'), '个人中心应只更改显示名称')
      await button(page, '退出登录').filter({ visible: true }).first().click()
      await page.waitForURL(`${origin}/login`)
    } finally {
      await page.context().close()
    }
  })

  const admin = await openPage({ viewport: { width: 1440, height: 1000 } })
  const student = await openPage({ viewport: { width: 1440, height: 1000 } })
  const otherStudent = await openPage()
  const [adminToken, studentToken, otherToken] = await step('管理员与两名学生分别登录', () => Promise.all([
    login(admin, accounts.admin, '/admin'),
    login(student, accounts.student, '/student'),
    login(otherStudent, accounts.student2, '/student'),
  ]))

  await step('学生越权路由、管理员 API 及匿名访问', async () => {
    await student.evaluate(() => {
      history.pushState(null, '', '/admin')
      dispatchEvent(new PopStateEvent('popstate'))
    })
    await visible(student.getByRole('heading', { name: '没有访问权限', exact: true }))
    expectForbid(await api(student, studentToken, 'GET', '/admin/statistics', [403]))
    expectForbid(await api(student, studentToken, 'GET', '/admin/users', [403]))
    expectForbid(await api(admin, adminToken, 'GET', '/enrollments/me', [403]))
    check((await api(student, null, 'GET', '/admin/statistics', [401])).code === 'UNAUTHORIZED',
      '匿名请求应返回 UNAUTHORIZED')
    await nav(student, '/student').click()
    await student.waitForURL(`${origin}/student`)
  })

  await step('管理员工作台加载并展示真实统计', async () => {
    const { data } = await api(admin, adminToken, 'GET', '/admin/statistics', [200])
    check(data.users >= 3 && data.activities > 0 && data.pendingActivities >= 0 && data.totalEnrollments >= 0,
      '统计没有包含已初始化的演示业务数据')
    check(Object.values(data.activityStatusCounts).reduce((sum, count) => sum + count, 0) === data.activities,
      '活动状态分布应与活动总数一致')
    const userMetric = admin.getByRole('link', { name: /用户总数/ }).locator('strong')
    await visible(userMetric)
    check(await userMetric.textContent() === new Intl.NumberFormat('zh-CN').format(data.users),
      '管理员页面中的用户总数应与统计 API 一致')
    await visible(admin.getByText(/活动状态分布/).first())
    await assertNoOverflow(admin)
    await capture(admin, 'admin-desktop')
  })

  const title = `浏览器验收课堂 ${suffix}`
  const activityId = await step('创建活动草稿并确认表单正常显示', async () => {
    await nav(admin, '/admin/activities').click()
    await admin.getByRole('link', { name: '新建活动', exact: true }).click()
    await admin.getByLabel('活动名称', { exact: true }).fill(title)
    await admin.getByLabel('活动分类').selectOption('ACADEMIC')
    await admin.getByLabel('活动地点', { exact: true }).fill('校园演示楼 101')
    const tomorrow = Date.now() + 3 * 86_400_000
    await admin.getByLabel('开始时间', { exact: true }).fill(localDateTime(new Date(tomorrow)))
    await admin.getByLabel('结束时间', { exact: true }).fill(localDateTime(new Date(tomorrow + 7_200_000)))
    await admin.getByLabel('报名上限', { exact: true }).fill('12')
    await admin.getByLabel('活动介绍').fill('用真实演示接口验证活动管理与学生报名。')
    await assertNoOverflow(admin)
    await capture(admin, 'activity-form-desktop')
    const data = await fromControl(admin, 'POST', '/admin/activities', () => button(admin, '保存草稿').click(), [201])
    check(data.title === title && data.status === 'DRAFT', '活动应创建为草稿')
    await admin.waitForURL(`${origin}/admin/activities/${data.id}`)
    return data.id
  })

  const managePath = `/admin/activities/${activityId}`
  const detailPath = `/student/activities/${activityId}`

  await step('从待审核列表完成审核并发布活动', async () => {
    const submitted = await confirm(admin, '提交审核', '确认提交', 'POST', `${managePath}/submit`)
    check(submitted.status === 'SUBMITTED', '提交后状态应为 SUBMITTED')
    const reviewQueue = await fromControl(admin, 'GET', '/admin/activities', () => nav(admin, '/admin/review').click(),
      [200], params => params.get('status') === 'SUBMITTED')
    check(reviewQueue.items.some(item => item.id === activityId), '待审核列表缺少刚提交的活动')
    await admin.getByRole('link', { name: title, exact: true }).click()
    const rejected = await confirm(admin, '审核拒绝', '确认拒绝', 'POST', `${managePath}/review`, async dialog => {
      await dialog.getByLabel('审核意见').fill('请补充现场签到说明。')
    })
    check(rejected.status === 'REJECTED' && rejected.reviewReason === '请补充现场签到说明。',
      '驳回应保存审核意见')
    await nav(admin, `${managePath}/edit`).click()
    await admin.getByLabel('活动介绍').fill('用真实演示接口验证活动管理与学生报名。到场后请在门口签到。')
    const edited = await fromControl(admin, 'PATCH', managePath, () => button(admin, '保存草稿').click())
    check(edited.status === 'DRAFT' && edited.version > rejected.version, '编辑被驳回的活动应生成新版草稿')
    await confirm(admin, '提交审核', '确认提交', 'POST', `${managePath}/submit`)
    const approved = await confirm(admin, '审核通过', '确认通过', 'POST', `${managePath}/review`, async dialog => {
      await dialog.getByLabel('审核意见').fill('演示活动安排与资料齐全。')
      await capture(admin, 'review-dialog-desktop')
    })
    check(approved.status === 'APPROVED', '审核通过后状态应为 APPROVED')
    const published = await confirm(admin, '发布活动', '确认发布', 'POST', `${managePath}/publish`)
    check(published.status === 'PUBLISHED', '发布后状态应为 PUBLISHED')
  })

  await step('学生浏览活动并观察加载状态', async () => {
    const matcher = url => url.origin === origin && url.pathname === '/api/v1/activities' && url.searchParams.has('page')
    let markHeld
    const held = new Promise(resolve => { markHeld = resolve })
    let release
    const released = new Promise(resolve => { release = resolve })
    let finishRoute
    const routeFinished = new Promise(resolve => { finishRoute = resolve })
    await student.route(matcher, async route => {
      markHeld()
      await released
      try {
        await route.continue()
        finishRoute(null)
      } catch (error) {
        finishRoute(error)
      }
    }, { times: 1 })
    const listed = observe(student, 'GET', '/activities', params => params.has('page')).then(
      response => ({ response }),
      error => ({ error }),
    )
    let holdDeadline
    try {
      await nav(student, '/student/activities').click()
      const expired = new Promise((_, reject) => {
        holdDeadline = setTimeout(() => reject(new Error('未拦截到活动列表请求')), timeout)
      })
      await Promise.all([
        Promise.race([held, expired]),
        visible(student.getByText(/正在加载|加载中/).first()),
      ])
    } finally {
      clearTimeout(holdDeadline)
      release()
    }
    const [routeError, result] = await Promise.all([routeFinished, listed])
    if (routeError) throw routeError
    if (result.error) throw result.error
    await verifyResponse(result.response, [200])
    await visible(student.getByTestId('activity-card').first())
    await assertNoOverflow(student)
    await capture(student, 'student-desktop')
  })

  await step('活动关键词搜索、分类筛选、空结果与详情', async () => {
    await student.getByLabel('搜索活动', { exact: true }).fill(title)
    const searched = await fromControl(student, 'GET', '/activities', () => button(student, '搜索').click(),
      [200], params => params.get('keyword') === title)
    check(searched.items.some(item => item.id === activityId), '关键词搜索缺少刚发布的活动')
    const categories = student.getByLabel('活动分类')
    const empty = await fromControl(student, 'GET', '/activities', () => categories.selectOption('SPORTS'),
      [200], params => params.get('category') === 'SPORTS')
    check(empty.totalElements === 0, '运动分类不应包含这项学术活动')
    await visible(student.getByText(/没有符合|未找到|没有找到|暂无活动|暂无符合/).first())
    const academic = await fromControl(student, 'GET', '/activities', () => categories.selectOption('ACADEMIC'),
      [200], params => params.get('category') === 'ACADEMIC')
    check(academic.items.some(item => item.id === activityId), '学术分类缺少刚发布的活动')
    const startsFrom = new Date(Date.now() + 2 * 86_400_000)
    await student.getByLabel('起始时间', { exact: true }).fill(localDateTime(startsFrom))
    const upcoming = await fromControl(student, 'GET', '/activities', () => button(student, '搜索').click(),
      [200], params => Boolean(params.get('startsFrom')))
    check(upcoming.items.some(item => item.id === activityId), '按开始时间筛选后缺少这项活动')
    const detail = await fromControl(student, 'GET', `/activities/${activityId}`, () => nav(student, detailPath).click())
    check(detail.id === activityId && detail.remainingSeats === 12, '详情应显示该活动的初始剩余名额')
    await visible(student.getByTestId('activity-detail'))
  })

  const record = await step('学生报名并在我的活动查看报名记录', async () => {
    await button(student, '立即报名').click()
    const dialog = student.getByRole('dialog')
    await visible(dialog)
    const enrolled = await fromControl(student, 'POST', `/activities/${activityId}/enrollments`,
      () => button(dialog, '确认报名').click(), [201])
    check(enrolled.status === 'ENROLLED' && enrolled.activityId === activityId, '报名数据与所选活动不一致')
    const mine = await fromControl(student, 'GET', '/enrollments/me', () => nav(student, '/student/enrollments').click())
    check(mine.items.some(item => item.id === enrolled.id), '个人报名列表缺少新记录')
    const upcoming = await fromControl(student, 'GET', '/enrollments/me', () => button(student, '待参加').click(),
      [200], params => params.get('status') === 'ENROLLED' && params.get('phase') === 'UPCOMING')
    check(upcoming.items.some(item => item.id === enrolled.id), '待参加筛选缺少刚报名的未来活动')
    const row = student.getByTestId('enrollment-row').filter({ hasText: title })
    const details = await fromControl(student, 'GET', `/enrollments/me/${enrolled.id}`, () => row.click())
    check(details.id === enrolled.id && details.activityTitle === title, '个人报名详情应与所选记录一致')
    return enrolled
  })

  await step('管理员查看含实际报名记录的名册', async () => {
    const [roster, summary] = await Promise.all([
      fromControl(admin, 'GET', `${managePath}/enrollments`, () => nav(admin, `${managePath}/enrollments`).click()),
      observe(admin, 'GET', `${managePath}/enrollments/summary`).then(response => verifyResponse(response, [200])),
    ])
    check(roster.items.some(item => item.id === record.id && item.username === accounts.student.username),
      '名册应包含当前学生的报名')
    check(summary.data.active === 1 && summary.data.total === 1, '名册汇总与报名结果不一致')
    await visible(admin.getByText(accounts.student.username, { exact: true }))
    await assertNoOverflow(admin)
    await capture(admin, 'enrollment-roster-desktop')
  })

  await step('学生取消报名并在通知中心标记已读', async () => {
    await nav(student, detailPath).click()
    const cancelled = await confirm(student, '取消报名', '确认取消', 'DELETE', `/activities/${activityId}/enrollments/me`)
    check(cancelled.id === record.id && cancelled.status === 'CANCELLED', '取消应更新原报名记录')
    await nav(student, '/student/enrollments').click()
    const cancelledRecords = await fromControl(student, 'GET', '/enrollments/me', () => button(student, '已取消').click(),
      [200], params => params.get('status') === 'CANCELLED')
    check(cancelledRecords.items.some(item => item.id === record.id), '已取消筛选缺少刚取消的报名')
    const inbox = await fromControl(student, 'GET', '/notifications/me', () => nav(student, '/student/notifications').click())
    const notice = inbox.items.find(item => item.activityId === activityId && item.type === 'ENROLLMENT_CANCELLED')
    check(notice?.readAt === null, '取消后应生成一条未读通知')
    const row = student.getByTestId('notification-row').filter({ hasText: '报名已取消' }).first()
    const details = await fromControl(student, 'GET', `/notifications/${notice.id}`, () => row.click())
    check(details.readAt === null, '浏览详情应保留未读状态')
    const dialog = student.getByRole('dialog', { name: '通知详情' })
    const read = await fromControl(student, 'PATCH', `/notifications/${notice.id}/read`, () => button(dialog, '标记已读').click())
    check(Boolean(read.readAt), '标记已读后服务端应返回已读时间')
    await visible(dialog.getByText('已读', { exact: true }))
    await button(dialog, '关闭通知').click()
    const all = await confirm(student, '全部已读', '确认全部已读', 'PATCH', '/notifications/me/read-all')
    check(all.updatedCount >= 1, '全部已读应处理剩余未读通知')
    const unread = await api(student, studentToken, 'GET', '/notifications/me/unread-count', [200])
    check(unread.data.unreadCount === 0, '全部已读后未读数应为零')

    const denied = await Promise.all([
      api(otherStudent, otherToken, 'GET', `/enrollments/me/${record.id}`, [404]),
      api(otherStudent, otherToken, 'GET', `/activities/${activityId}/enrollments/me`, [404]),
      api(otherStudent, otherToken, 'GET', `/notifications/${notice.id}`, [404]),
      api(otherStudent, otherToken, 'PATCH', `/notifications/${notice.id}/read`, [404]),
    ])
    check(denied.every(response => response.code === 'NOT_FOUND'), '其他学生的资源应返回 NOT_FOUND')
  })

  await step('用户搜索、禁用、恢复和旧令牌失效', async () => {
    await nav(admin, '/admin/users').click()
    await admin.getByLabel('用户名', { exact: true }).fill(accounts.student2.username)
    const users = await fromControl(admin, 'GET', '/admin/users', () => button(admin, '搜索').click(),
      [200], params => params.get('username') === accounts.student2.username)
    const account = users.items.find(item => item.username === accounts.student2.username)
    check(account?.status === 'ACTIVE', '第二名演示学生应为启用状态')
    const userPath = `/admin/users/${account.id}`
    const profile = await fromControl(admin, 'GET', userPath, () => nav(admin, userPath).click())
    check(profile.roles.includes('STUDENT'), '用户详情应显示学生角色')
    const statusPath = `${userPath}/status`
    let restored = false
    try {
      const disabled = await confirm(admin, '禁用账号', '确认禁用', 'PATCH', statusPath)
      check(disabled.status === 'DISABLED', '禁用后账号状态应为 DISABLED')
      await fromControl(otherStudent, 'GET', '/activities', () => nav(otherStudent, '/student/activities').click(), [401])
      await otherStudent.waitForURL(`${origin}/login`)
      await visible(otherStudent.getByText('会话已失效，请重新登录', { exact: true }))
      const active = await confirm(admin, '恢复账号', '确认恢复', 'PATCH', statusPath)
      check(active.status === 'ACTIVE', '恢复后账号状态应为 ACTIVE')
      restored = true
      check((await api(otherStudent, otherToken, 'GET', '/users/me', [401])).code === 'UNAUTHORIZED',
        '恢复账号后旧令牌仍需失效')
      await login(otherStudent, accounts.student2, '/student')
    } finally {
      if (!restored) await api(admin, adminToken, 'PATCH', statusPath, [200], { status: 'ACTIVE' })
    }
  })

  await step('操作审计显示真实审核记录', async () => {
    const operations = await fromControl(admin, 'GET', '/admin/operation-logs', () => nav(admin, '/admin/audit').click())
    check(operations.items.some(item => item.action === 'ACTIVITY_APPROVED' && item.targetId === activityId),
      '操作审计缺少刚完成的审核记录')
    const activityRows = admin.getByRole('table', { name: '操作日志' }).getByRole('row').filter({
      has: admin.getByRole('link', { name: `活动 #${activityId}`, exact: true }),
    })
    await visible(activityRows.getByRole('cell', { name: '审核通过', exact: true }))
    const nextPage = await fromControl(admin, 'GET', '/admin/operation-logs', () => button(admin, '下一页').click(),
      [200], params => params.get('page') === '1')
    check(nextPage.page === 1 && nextPage.items.length > 0, '审计日志分页应加载第二页')
  })

  await step('活动请求失败时可在页面内重试', async () => {
    const matcher = url => url.origin === origin && url.pathname === '/api/v1/activities' && url.searchParams.has('page')
    await student.route(matcher, route => route.abort('failed'), { times: 1 })
    await nav(student, '/student/activities').click()
    const retry = student.getByRole('button', { name: /重试/ }).first()
    await visible(retry)
    await fromControl(student, 'GET', '/activities', () => retry.click())
    await visible(student.getByTestId('activity-card').first())
  })

  await step('手机布局、导航与页面滚动', async () => {
    const mobile = await openPage({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })
    await login(mobile, accounts.student, '/student')
    await button(mobile, '打开导航菜单').click()
    const studentDrawer = mobile.getByRole('complementary', { name: '学生导航' })
    await studentDrawer.getByRole('link', { name: '活动广场', exact: true }).click()
    await mobile.waitForURL(`${origin}/student/activities`)
    await visible(mobile.getByTestId('activity-card').first())
    await assertNoOverflow(mobile)
    const scrolls = await mobile.evaluate(() => {
      window.scrollTo(0, document.documentElement.scrollHeight)
      const scrolled = window.scrollY > 0
      window.scrollTo(0, 0)
      return scrolled
    })
    check(scrolls, '手机活动列表应能纵向滚动')
    await capture(mobile, 'student-mobile')

    const mobileAdmin = await openPage({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true })
    await login(mobileAdmin, accounts.admin, '/admin')
    await button(mobileAdmin, '打开导航菜单').click()
    const adminDrawer = mobileAdmin.getByRole('complementary', { name: '管理员导航' })
    await adminDrawer.getByRole('link', { name: '操作审计', exact: true }).click()
    await visible(mobileAdmin.locator('table[aria-label="操作日志"] tbody tr').first())
    await assertNoOverflow(mobileAdmin)
    await capture(mobileAdmin, 'admin-audit-mobile')

    await button(mobileAdmin, '打开导航菜单').click()
    await adminDrawer.getByRole('link', { name: '活动管理', exact: true }).click()
    await nav(mobileAdmin, '/admin/activities/new').click()
    await visible(mobileAdmin.getByLabel('活动名称', { exact: true }))
    await assertNoOverflow(mobileAdmin)
    await capture(mobileAdmin, 'activity-form-mobile')
  })

  console.log('CampusFlow 浏览器验收通过。业务页面截图已保存至 .campusflow-demo/acceptance。')
} catch (error) {
  let safeMessage = error instanceof Error ? error.message : '未知错误'
  for (const secret of secrets) safeMessage = safeMessage.replaceAll(secret, '[已隐藏]')
  safeMessage = safeMessage.replace(/\beyJ[\w-]+\.[\w-]+\.[\w-]+\b/g, '[已隐藏]')
  console.error(`未通过 · ${stepName}\n${safeMessage}`)
  process.exitCode = 1
} finally {
  await browser?.close()
}
