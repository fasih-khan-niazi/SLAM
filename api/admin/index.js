const path = require('path')
const { Op } = require('sequelize')
const adminjs = require('adminjs')
const AdminJS = adminjs.default || adminjs
const { ComponentLoader } = adminjs
const AdminJSExpress = require('@adminjs/express')
const AdminJSSequelize = require('@adminjs/sequelize')
const { sendEmail } = require('../utils/email')
const { notifyUser } = require('../utils/notify')
const { cancelOtherActiveSubscriptions } = require('../utils/subscription')
const { normalizeMerchant, envMerchant } = require('../utils/config')
const {
  User,
  SubscriptionPlan,
  Subscription,
  Payment,
  LocationLog,
  SystemConfig,
  Notification,
} = require('../models')

AdminJS.registerAdapter(AdminJSSequelize)

const navOperations = { name: 'Operations', icon: 'Activity' }
const navBilling = { name: 'Billing', icon: 'CreditCard' }
const navProduct = { name: 'Product', icon: 'Settings' }

const componentLoader = new ComponentLoader()
const Dashboard = componentLoader.add('SlamDashboard', path.join(__dirname, 'dashboard'))
componentLoader.override('Login', path.join(__dirname, 'login'))

function operatorEmail(currentAdmin) {
  return (currentAdmin && currentAdmin.email) || 'admin'
}

function normalizePinPayload(payload) {
  if (!payload) return payload
  const next = { ...payload }
  let min = Number(next.pin_min_length)
  let max = Number(next.pin_max_length)
  if (Number.isFinite(min) && Number.isFinite(max) && max < min) {
    next.pin_max_length = min
  }
  if ('easypaisa_account' in next) {
    const cleaned = normalizeMerchant(next.easypaisa_account, '')
    next.easypaisa_account = cleaned || envMerchant('EASYPAY_ACCOUNT', 'PAYMENT_ACCOUNT')
  }
  if ('jazzcash_account' in next) {
    const cleaned = normalizeMerchant(next.jazzcash_account, '')
    next.jazzcash_account = cleaned || envMerchant('JAZZCASH_ACCOUNT', 'PAYMENT_ACCOUNT')
  }
  return next
}

async function countFreeActivePlans(excludeId = null) {
  const where = {
    price_pkr: 0,
    is_active: true,
  }
  if (excludeId != null) {
    where.id = { [Op.ne]: excludeId }
  }
  return SubscriptionPlan.count({ where })
}

const admin = new AdminJS({
  componentLoader,
  assets: {
    styles: ['/admin.css'],
  },
  dashboard: {
    component: Dashboard,
    handler: async () => {
      const [pendingPayments, users, activePlans, config] = await Promise.all([
        Payment.count({ where: { status: 'pending' } }),
        User.count(),
        SubscriptionPlan.count({ where: { is_active: true } }),
        SystemConfig.findOne({ order: [['id', 'ASC']] }),
      ])
      return {
        pendingPayments,
        users,
        activePlans,
        smsPrefix: config ? config.sms_prefix : 'SLAM',
        emergencyEnabled: config ? Boolean(config.emergency_enabled) : true,
        maintenance: config ? Boolean(config.maintenance) : false,
        paymentsEnabled: config ? config.payments_enabled !== false : true,
      }
    },
  },
  branding: {
    companyName: 'SLAM',
    logo: false,
    withMadeWithLove: false,
    favicon: false,
    theme: {
      // AdminJS grey100 = primary text (must be dark on light surfaces)
      colors: {
        primary100: '#0f766e',
        primary80: '#0d9488',
        primary60: '#14b8a6',
        primary40: '#5eead4',
        primary20: '#ccfbf1',
        accent: '#0d9488',
        love: '#0f766e',
        favourites: '#0f766e',
        info: '#0d9488',
        success: '#16a34a',
        error: '#dc2626',
        warning: '#d97706',
        filterBg: '#ffffff',
        hoverBg: '#ccfbf1',
        filter: '#eeeeee',
        bg: '#f5f5f5',
        border: '#b0b0b0',
        inputBorder: '#b0b0b0',
        separatorBg: '#b0b0b0',
        highlight: '#ccfbf1',
        light: '#ffffff',
        white: '#ffffff',
        grey100: '#171717',
        grey80: '#404040',
        grey60: '#737373',
        grey40: '#a3a3a3',
        grey20: '#eeeeee',
        grey60inverted: '#a3a3a3',
        sidebar: '#ffffff',
        container: '#ffffff',
        defaultText: '#171717',
        lightText: '#737373',
      },
      borders: {
        default: '1px solid #b0b0b0',
        input: '1px solid #b0b0b0',
      },
      font: '"Segoe UI", "IBM Plex Sans", system-ui, sans-serif',
      space: {
        default: 16,
      },
      shadows: {
        login: '0 12px 32px rgba(23, 23, 23, 0.08)',
        cardHover: '0 16px 40px rgba(23, 23, 23, 0.12)',
      },
    },
  },
  locale: {
    language: 'en',
    translations: {
      en: {
        labels: {
          User: 'Users',
          SubscriptionPlan: 'Plans',
          Subscription: 'Subscriptions',
          Payment: 'Payments',
          LocationLog: 'Location logs',
          SystemConfig: 'Config',
          Notification: 'Notifications',
        },
        resources: {
          SystemConfig: {
            properties: {
              easypaisa_account: 'EasyPaisa account',
              jazzcash_account: 'JazzCash account',
            },
          },
          Payment: {
            properties: {
              reviewed_by: 'Reviewed by',
            },
          },
        },
        components: {
          Login: {
            welcomeHeader: 'SLAM Admin',
            welcomeMessage: 'Sign in to review payments and product settings.',
            loginButton: 'Sign in',
          },
        },
      },
    },
  },
  resources: [
    {
      resource: User,
      options: {
        navigation: navOperations,
        properties: {
          password_hash: { isVisible: false },
          pin_salt: { isVisible: false },
          pin_verifier: { isVisible: false },
        },
        listProperties: ['id', 'name', 'email', 'phone', 'role', 'createdAt'],
        filterProperties: ['name', 'email', 'role'],
        editProperties: ['name', 'email', 'phone', 'role'],
        showProperties: ['id', 'name', 'email', 'phone', 'role', 'createdAt', 'updatedAt'],
        actions: {
          delete: {
            isAccessible: ({ record }) => record && record.params.role !== 'admin',
            guard: 'Delete this user account? This cannot be undone.',
          },
          bulkDelete: {
            isAccessible: false,
          },
        },
      },
    },
    {
      resource: LocationLog,
      options: {
        navigation: navOperations,
        listProperties: ['id', 'user_id', 'latitude', 'longitude', 'accuracy_meters', 'requested_by', 'createdAt'],
        actions: {
          new: { isAccessible: false },
          edit: { isAccessible: false },
          delete: { isAccessible: false },
          bulkDelete: { isAccessible: false },
        },
      },
    },
    {
      resource: Notification,
      options: {
        navigation: navOperations,
        listProperties: ['id', 'user_id', 'title', 'kind', 'read_at', 'createdAt'],
        filterProperties: ['user_id', 'kind'],
      },
    },
    {
      resource: Payment,
      options: {
        navigation: navBilling,
        listProperties: [
          'id',
          'user_id',
          'plan_id',
          'amount_pkr',
          'payment_method',
          'transaction_id',
          'status',
          'reviewed_by',
          'createdAt',
        ],
        showProperties: [
          'id',
          'user_id',
          'plan_id',
          'subscription_id',
          'amount_pkr',
          'payment_method',
          'transaction_id',
          'screenshot_url',
          'status',
          'reviewed_by',
          'approved_at',
          'createdAt',
        ],
        filterProperties: ['status', 'payment_method', 'user_id', 'plan_id'],
        properties: {
          screenshot_url: {
            type: 'string',
            props: { type: 'url' },
            isVisible: { list: false, filter: false, show: true, edit: false },
          },
          reviewed_by: {
            isVisible: { list: true, filter: false, show: true, edit: false },
          },
          status: {
            availableValues: [
              { value: 'pending', label: 'Pending' },
              { value: 'approved', label: 'Approved' },
              { value: 'rejected', label: 'Rejected' },
            ],
          },
        },
        actions: {
          new: { isAccessible: false },
          edit: { isAccessible: false },
          delete: { isAccessible: false },
          bulkDelete: { isAccessible: false },
          approve: {
            actionType: 'record',
            icon: 'CircleCheck',
            label: 'Approve',
            guard: 'Approve this payment and activate the subscription?',
            isVisible: (context) => context.record && context.record.params.status === 'pending',
            handler: async (request, response, context) => {
              const { record, currentAdmin } = context
              const paymentId = record.params.id
              const reviewer = operatorEmail(currentAdmin)

              try {
                await Payment.update(
                  {
                    status: 'approved',
                    approved_at: new Date(),
                    reviewed_by: reviewer,
                  },
                  { where: { id: paymentId } }
                )

                const payment = await Payment.findByPk(paymentId, {
                  include: [
                    { model: User },
                    { model: SubscriptionPlan, as: 'plan' },
                  ],
                })

                const today = new Date()
                const endDate = new Date()
                endDate.setDate(endDate.getDate() + 30)

                await cancelOtherActiveSubscriptions(payment.user_id, payment.subscription_id)

                await Subscription.update(
                  { status: 'active', start_date: today, end_date: endDate },
                  { where: { id: payment.subscription_id } }
                )

                if (payment.User) {
                  await notifyUser(
                    payment.user_id,
                    'Payment approved',
                    `Your ${payment.plan ? payment.plan.name : ''} plan is now active.`,
                    'payment',
                  )
                  await sendEmail(
                    payment.User.email,
                    'Your SLAM subscription is active',
                    `Hi ${payment.User.name},\n\nYour ${payment.plan ? payment.plan.name : ''} plan is now active until ${endDate.toDateString()}.\n`
                  )
                }

                console.log(`Payment ${paymentId} approved by ${reviewer}`)
                const updatedRecord = await context.resource.findOne(paymentId)
                return {
                  record: updatedRecord.toJSON(currentAdmin),
                  notice: {
                    message: `Payment approved by ${reviewer}. Subscription is active.`,
                    type: 'success',
                  },
                }
              } catch (err) {
                console.error('Admin approve error:', err)
                return {
                  record: record.toJSON(currentAdmin),
                  notice: { message: err.message, type: 'error' },
                }
              }
            },
          },
          reject: {
            actionType: 'record',
            icon: 'CircleX',
            label: 'Reject',
            guard: 'Reject this payment?',
            isVisible: (context) => context.record && context.record.params.status === 'pending',
            handler: async (request, response, context) => {
              const { record, currentAdmin } = context
              const paymentId = record.params.id
              const reviewer = operatorEmail(currentAdmin)

              try {
                const payment = await Payment.findByPk(paymentId, {
                  include: [{ model: User }],
                })

                await Payment.update(
                  { status: 'rejected', reviewed_by: reviewer },
                  { where: { id: paymentId } }
                )

                await Subscription.update(
                  { status: 'pending_payment' },
                  { where: { id: payment.subscription_id } }
                )

                if (payment.User) {
                  await notifyUser(
                    payment.user_id,
                    'Payment rejected',
                    `We could not verify transaction ${payment.transaction_id}.`,
                    'payment',
                  )
                  await sendEmail(
                    payment.User.email,
                    'SLAM payment could not be verified',
                    `Hi ${payment.User.name},\n\nWe could not verify transaction ${payment.transaction_id}. Please submit again.\n`
                  )
                }

                console.log(`Payment ${paymentId} rejected by ${reviewer}`)
                const updatedRecord = await context.resource.findOne(paymentId)
                return {
                  record: updatedRecord.toJSON(currentAdmin),
                  notice: { message: `Payment rejected by ${reviewer}.`, type: 'success' },
                }
              } catch (err) {
                console.error('Admin reject error:', err)
                return {
                  record: record.toJSON(currentAdmin),
                  notice: { message: err.message, type: 'error' },
                }
              }
            },
          },
        },
      },
    },
    {
      resource: Subscription,
      options: {
        navigation: navBilling,
        listProperties: ['id', 'user_id', 'plan_id', 'status', 'requests_used', 'start_date', 'end_date'],
        filterProperties: ['status', 'user_id', 'plan_id'],
      },
    },
    {
      resource: SubscriptionPlan,
      options: {
        navigation: navProduct,
        listProperties: [
          'id',
          'name',
          'price_pkr',
          'monthly_limit',
          'max_contacts',
          'has_history',
          'is_active',
        ],
        editProperties: [
          'name',
          'price_pkr',
          'monthly_limit',
          'max_contacts',
          'has_history',
          'description',
          'is_active',
        ],
        filterProperties: ['name', 'is_active'],
        properties: {
          monthly_limit: {
            description: 'Null or empty = unlimited locates per billing period.',
          },
          is_active: {
            description: 'Inactive plans are hidden from the portal catalog. Existing subscribers keep access until their period ends.',
          },
        },
        actions: {
          delete: {
            isAccessible: async (context) => {
              if (!context.record) return false
              const planId = context.record.params.id
              const [subs, payments] = await Promise.all([
                Subscription.count({ where: { plan_id: planId } }),
                Payment.count({ where: { plan_id: planId } }),
              ])
              return subs === 0 && payments === 0
            },
            guard: 'Permanently delete this plan? Prefer deactivating (is_active off) when the plan has history.',
          },
          bulkDelete: { isAccessible: false },
          edit: {
            before: async (request) => {
              if (request.method !== 'post') return request
              const payload = { ...request.payload }
              const planId = request.params?.recordId
              const existing = planId ? await SubscriptionPlan.findByPk(planId) : null

              const nextActive = payload.is_active === true
                || payload.is_active === 'true'
                || payload.is_active === 'on'
                || payload.is_active === 1
                || payload.is_active === '1'
              const wasFree = existing && Number(existing.price_pkr) === 0 && existing.is_active
              const nextPrice = payload.price_pkr != null ? Number(payload.price_pkr) : (existing ? Number(existing.price_pkr) : null)
              const deactivatingFree = wasFree && !nextActive
              const removingFreePrice = wasFree && nextPrice !== 0

              if (deactivatingFree || removingFreePrice) {
                const others = await countFreeActivePlans(existing.id)
                if (others === 0) {
                  throw new Error(
                    'Cannot deactivate or remove the last Free (price 0) plan. Create another free tier first.'
                  )
                }
              }

              if (payload.monthly_limit === '' || payload.monthly_limit === 'null') {
                payload.monthly_limit = null
              }
              request.payload = payload
              return request
            },
          },
          new: {
            before: async (request) => {
              if (request.method !== 'post') return request
              const payload = { ...request.payload }
              if (payload.monthly_limit === '' || payload.monthly_limit === 'null') {
                payload.monthly_limit = null
              }
              request.payload = payload
              return request
            },
          },
        },
      },
    },
    {
      resource: SystemConfig,
      options: {
        navigation: navProduct,
        listProperties: [
          'sms_prefix',
          'login_attempt_cap',
          'pin_attempt_cap',
          'emergency_enabled',
          'emergency_interval_hours',
          'maintenance',
          'payments_enabled',
          'easypaisa_account',
          'jazzcash_account',
        ],
        editProperties: [
          'sms_prefix',
          'pin_min_length',
          'pin_max_length',
          'login_attempt_cap',
          'login_window_minutes',
          'pin_attempt_cap',
          'pin_window_minutes',
          'emergency_enabled',
          'emergency_interval_hours',
          'maintenance',
          'payments_enabled',
          'maps_enabled',
          'email_enabled',
          'easypaisa_account',
          'jazzcash_account',
        ],
        properties: {
          login_attempt_cap: {
            label: 'Portal login — max failed attempts',
            description: 'Wrong email/password tries on the website or phone login. After this many from one network, sign-in is blocked until the portal window ends.',
          },
          login_window_minutes: {
            label: 'Portal login — window (minutes)',
            description: 'How long failed portal logins are counted. Default 15.',
          },
          pin_attempt_cap: {
            label: 'SMS PIN — max wrong attempts',
            description: 'Wrong SLAM [PIN] LOCATE texts on the phone. After this many, locates stay silent until the SMS window ends.',
          },
          pin_window_minutes: {
            label: 'SMS PIN — window (minutes)',
            description: 'How long wrong SMS PINs are counted. Default 15. Updating the PIN on the phone clears the count.',
          },
          emergency_enabled: {
            label: 'Emergency — available on phones',
            description: 'When off, the Emergency toggle is hidden on the app.',
          },
          emergency_interval_hours: {
            label: 'Emergency — hours between SMS',
            description: 'Integer 1 to 24. The phone texts trusted numbers on this interval while Emergency is on. No server cron — the phone sends the SMS.',
          },
          maintenance: {
            label: 'Maintenance mode',
            description: 'Blocks plan upgrades and payment submit on the web portal. Shows a banner on web and Android.',
          },
          payments_enabled: {
            label: 'Payments enabled',
            description: 'When off, users cannot choose paid plans or submit payment screenshots on the portal.',
          },
          maps_enabled: {
            label: 'Maps enabled (reserved)',
            description: 'Reserved for a future maps UI. Does not change SMS map links today.',
          },
          email_enabled: {
            label: 'Transactional email',
            description: 'When off, the API skips sending email (payment notices, password reset still depend on this gate).',
          },
          easypaisa_account: {
            label: 'EasyPaisa account',
            description: 'Shown on the web payments page. Digits only, 10–15 characters. Blank falls back to env/default.',
          },
          jazzcash_account: {
            label: 'JazzCash account',
            description: 'Shown on the web payments page. Digits only, 10–15 characters. Blank falls back to env/default.',
          },
        },
        actions: {
          new: { isAccessible: false },
          delete: { isAccessible: false },
          bulkDelete: { isAccessible: false },
          edit: {
            layout: [
              ['sms_prefix'],
              ['pin_min_length', 'pin_max_length'],
              ['login_attempt_cap', 'login_window_minutes'],
              ['pin_attempt_cap', 'pin_window_minutes'],
              ['emergency_enabled', 'emergency_interval_hours'],
              ['maintenance', 'payments_enabled'],
              ['maps_enabled', 'email_enabled'],
              ['easypaisa_account', 'jazzcash_account'],
            ],
            before: async (request) => {
              if (request.method === 'post') {
                request.payload = normalizePinPayload(request.payload)
              }
              return request
            },
          },
        },
      },
    },
  ],
  rootPath: '/admin',
})

// Stale empty .adminjs/bundle.js skips rebuild and drops custom Login/Dashboard.
function clearStaleAdminBundle() {
  const fs = require('fs')
  const tmpDir = path.join(__dirname, '..', '.adminjs')
  for (const file of ['bundle.js', 'entry.js']) {
    const target = path.join(tmpDir, file)
    try {
      if (!fs.existsSync(target)) continue
      const text = fs.readFileSync(target, 'utf8')
      if (!text.includes('SlamDashboard') && !text.includes('Login')) {
        fs.unlinkSync(target)
        console.log(`Cleared stale AdminJS ${file}`)
      }
    } catch (err) {
      console.warn(`Could not clear AdminJS ${file}:`, err.message)
    }
  }
}

clearStaleAdminBundle()

const shouldWatch =
  process.env.NODE_ENV !== 'production'
  && process.env.ADMINJS_SKIP_WATCH !== '1'
  && typeof admin.watch === 'function'

if (shouldWatch) {
  admin.watch().catch((err) => {
    console.warn('AdminJS watch failed:', err.message)
  })
}

const adminRouter = AdminJSExpress.buildAuthenticatedRouter(
  admin,
  {
    authenticate: async (email, password) => {
      if (email === process.env.ADMIN_EMAIL && password === process.env.ADMIN_PASSWORD) {
        return { email, role: 'admin' }
      }
      return null
    },
    cookiePassword: process.env.SESSION_SECRET,
  },
  null,
  {
    resave: false,
    saveUninitialized: false,
    proxy: true,
    rolling: true,
    name: 'slam.admin.sid',
    cookie: {
      httpOnly: true,
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'lax',
      maxAge: 8 * 60 * 60 * 1000,
    },
  }
)

module.exports = { admin, adminRouter }
