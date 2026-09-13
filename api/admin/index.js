const path = require('path')
const adminjs = require('adminjs')
const AdminJS = adminjs.default || adminjs
const { ComponentLoader } = adminjs
const AdminJSExpress = require('@adminjs/express')
const AdminJSSequelize = require('@adminjs/sequelize')
const { sendEmail } = require('../utils/email')
const { notifyUser } = require('../utils/notify')
const { cancelOtherActiveSubscriptions } = require('../utils/subscription')
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

const slamNav = { name: 'SLAM', icon: 'Map' }
const componentLoader = new ComponentLoader()
const Dashboard = componentLoader.add('SlamDashboard', path.join(__dirname, 'dashboard'))

const admin = new AdminJS({
  componentLoader,
  dashboard: {
    component: Dashboard,
    handler: async () => {
      const [pendingPayments, users, config] = await Promise.all([
        Payment.count({ where: { status: 'pending' } }),
        User.count(),
        SystemConfig.findOne({ order: [['id', 'ASC']] }),
      ])
      return {
        pendingPayments,
        users,
        emergencyEnabled: config ? Boolean(config.emergency_enabled) : true,
      }
    },
  },
  branding: {
    companyName: 'SLAM',
    logo: false,
    withMadeWithLove: false,
    favicon: false,
    theme: {
      colors: {
        primary100: '#0d9488',
        primary80: '#14b8a6',
        primary60: '#2dd4bf',
        primary40: '#5eead4',
        primary20: '#ccfbf1',
        accent: '#14b8a6',
        hoverBg: '#134e4a',
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
          SystemConfig: 'Product settings',
          Notification: 'Notifications',
        },
        components: {
          Login: {
            welcomeHeader: 'SLAM Admin',
            welcomeMessage: 'Sign in to review payments and product settings.',
          },
        },
      },
    },
  },
  resources: [
    {
      resource: User,
      options: {
        navigation: slamNav,
        properties: {
          password_hash: { isVisible: false },
        },
        listProperties: ['id', 'name', 'email', 'phone', 'role', 'createdAt'],
        filterProperties: ['name', 'email', 'role'],
      },
    },
    {
      resource: SubscriptionPlan,
      options: {
        navigation: slamNav,
        listProperties: ['id', 'name', 'price_pkr', 'monthly_limit', 'max_contacts', 'is_active'],
      },
    },
    {
      resource: Subscription,
      options: {
        navigation: slamNav,
        listProperties: ['id', 'user_id', 'plan_id', 'status', 'requests_used', 'start_date', 'end_date'],
        filterProperties: ['status', 'user_id'],
      },
    },
    {
      resource: Payment,
      options: {
        navigation: slamNav,
        listProperties: ['id', 'user_id', 'plan_id', 'amount_pkr', 'payment_method', 'transaction_id', 'status', 'createdAt'],
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
          'approved_at',
          'createdAt',
        ],
        filterProperties: ['status', 'payment_method'],
        actions: {
          approve: {
            actionType: 'record',
            icon: 'CircleCheck',
            label: 'Approve',
            guard: 'Approve this payment and activate the subscription?',
            isVisible: (context) => context.record && context.record.params.status === 'pending',
            handler: async (request, response, context) => {
              const { record, currentAdmin } = context
              const paymentId = record.params.id

              try {
                await Payment.update(
                  { status: 'approved', approved_at: new Date() },
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

                const updatedRecord = await context.resource.findOne(paymentId)
                return {
                  record: updatedRecord.toJSON(currentAdmin),
                  notice: {
                    message: 'Payment approved. Subscription is active.',
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

              try {
                const payment = await Payment.findByPk(paymentId, {
                  include: [{ model: User }],
                })

                await Payment.update(
                  { status: 'rejected' },
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

                const updatedRecord = await context.resource.findOne(paymentId)
                return {
                  record: updatedRecord.toJSON(currentAdmin),
                  notice: { message: 'Payment rejected.', type: 'success' },
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
      resource: LocationLog,
      options: {
        navigation: slamNav,
        listProperties: ['id', 'user_id', 'latitude', 'longitude', 'accuracy', 'requested_by', 'createdAt'],
      },
    },
    {
      resource: SystemConfig,
      options: {
        navigation: slamNav,
        listProperties: [
          'sms_prefix',
          'login_attempt_cap',
          'pin_attempt_cap',
          'emergency_enabled',
          'emergency_interval_hours',
          'maintenance',
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
        },
        actions: {
          new: { isAccessible: false },
          delete: { isAccessible: false },
          edit: {
            layout: [
              ['sms_prefix'],
              ['pin_min_length', 'pin_max_length'],
              ['login_attempt_cap', 'login_window_minutes'],
              ['pin_attempt_cap', 'pin_window_minutes'],
              ['emergency_enabled', 'emergency_interval_hours'],
              ['maintenance', 'payments_enabled'],
              ['maps_enabled', 'email_enabled'],
            ],
          },
        },
      },
    },
    {
      resource: Notification,
      options: {
        navigation: slamNav,
        listProperties: ['id', 'user_id', 'title', 'kind', 'read_at', 'createdAt'],
        filterProperties: ['user_id', 'kind'],
      },
    },
  ],
  rootPath: '/admin',
})

const adminRouter = AdminJSExpress.buildAuthenticatedRouter(
  admin,
  {
    authenticate: async (email, password) => {
      if (email === process.env.ADMIN_EMAIL && password === process.env.ADMIN_PASSWORD) {
        return { email }
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
    cookie: {
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'lax',
    },
  }
)

module.exports = { admin, adminRouter }
