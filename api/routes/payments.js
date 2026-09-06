const express = require('express')
const multer = require('multer')
const { protect, requireAdmin } = require('../middleware/auth')
const { sendEmail } = require('../utils/email')
const { isConfigured, uploadPaymentScreenshot } = require('../utils/cloudinary')
const { getSystemConfig } = require('../utils/config')
const { notifyAdmins, notifyUser } = require('../utils/notify')
const { ok, fail } = require('../utils/http')
const { cancelOtherActiveSubscriptions } = require('../utils/subscription')
const {
  Payment,
  Subscription,
  SubscriptionPlan,
  User,
} = require('../models')

const router = express.Router()

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 5 * 1024 * 1024 },
  fileFilter: (req, file, cb) => {
    const allowed = ['image/jpeg', 'image/jpg', 'image/png']
    if (allowed.includes(file.mimetype)) {
      cb(null, true)
    } else {
      cb(new Error('Only JPG and PNG images are allowed'))
    }
  },
})

router.post('/payments/submit', protect, upload.single('screenshot'), async (req, res) => {
  try {
    const { subscription_id, payment_method, transaction_id } = req.body
    const user_id = req.user.id

    if (!subscription_id || !payment_method || !transaction_id) {
      return fail(res, 400, 'subscription_id, payment_method and transaction_id are required')
    }

    if (!req.file) {
      return fail(res, 400, 'A JPG or PNG screenshot is required')
    }

    if (!['jazzcash', 'easypaisa'].includes(payment_method)) {
      return fail(res, 400, 'payment_method must be jazzcash or easypaisa')
    }

    const config = await getSystemConfig()
    if (config.maintenance) {
      return fail(res, 503, 'Service is paused for maintenance')
    }
    if (!config.payments_enabled) {
      return fail(res, 503, 'Payments are paused right now')
    }

    if (!isConfigured()) {
      return fail(res, 503, 'Payment screenshots are not configured')
    }

    const duplicate = await Payment.findOne({ where: { transaction_id } })
    if (duplicate) {
      return fail(res, 400, 'This transaction ID has already been submitted')
    }

    const subscription = await Subscription.findOne({
      where: { id: subscription_id, user_id },
      include: [{ model: SubscriptionPlan, as: 'plan' }],
    })

    if (!subscription) {
      return fail(res, 404, 'Subscription not found')
    }

    if (subscription.plan.price_pkr === 0) {
      return fail(res, 400, 'The Free plan does not require payment')
    }

    if (!['pending_payment', 'pending_approval'].includes(subscription.status)) {
      return fail(res, 400, 'This subscription is not waiting for payment')
    }

    const screenshotUrl = await uploadPaymentScreenshot(req.file.buffer, req.file.originalname)

    const payment = await Payment.create({
      user_id,
      plan_id: subscription.plan_id,
      subscription_id: subscription.id,
      amount_pkr: subscription.plan.price_pkr,
      payment_method,
      transaction_id: String(transaction_id).trim(),
      screenshot_url: screenshotUrl,
      status: 'pending',
    })

    await subscription.update({ status: 'pending_approval' })

    const adminInbox = process.env.ADMIN_EMAIL
    const publicApi = process.env.API_PUBLIC_URL || 'http://localhost:3000'

    await notifyAdmins(
      'New payment to review',
      `${req.user.name} submitted ${subscription.plan.name} (Rs ${subscription.plan.price_pkr}).`,
      'payment',
    )
    await notifyUser(
      user_id,
      'Payment submitted',
      `Your ${subscription.plan.name} receipt is waiting for review.`,
      'payment',
    )

    if (adminInbox) {
      await sendEmail(
        adminInbox,
        'SLAM — new payment to review',
        `A payment was submitted and needs review.\n\n` +
        `User: ${req.user.name} (${req.user.email})\n` +
        `Plan: ${subscription.plan.name}\n` +
        `Amount: Rs. ${subscription.plan.price_pkr}\n` +
        `Method: ${payment_method}\n` +
        `Transaction ID: ${transaction_id}\n` +
        `Screenshot: ${screenshotUrl}\n\n` +
        `Admin panel: ${publicApi}/admin`
      )
    }

    return ok(
      res,
      'Payment submitted. It will be reviewed shortly.',
      {
        payment_id: payment.id,
        status: payment.status,
        transaction_id: payment.transaction_id,
        payment_method: payment.payment_method,
        screenshot_url: payment.screenshot_url,
      },
      201
    )
  } catch (err) {
    console.error('Payment submit error:', err)
    return fail(res, 500, 'Unable to submit payment')
  }
})

router.get('/payments/my', protect, async (req, res) => {
  try {
    const payments = await Payment.findAll({
      where: { user_id: req.user.id },
      include: [{ model: SubscriptionPlan, as: 'plan' }],
      order: [['createdAt', 'DESC']],
    })

    const formatted = payments.map((p) => ({
      id: p.id,
      plan_name: p.plan ? p.plan.name : null,
      amount_pkr: p.amount_pkr,
      payment_method: p.payment_method,
      transaction_id: p.transaction_id,
      status: p.status,
      screenshot_url: p.screenshot_url,
      submitted_at: p.createdAt,
      approved_at: p.approved_at,
    }))

    return ok(res, 'Payment history fetched', { payments: formatted })
  } catch (err) {
    console.error('Get payments error:', err)
    return fail(res, 500, 'Unable to load payments')
  }
})

router.patch('/admin/payments/:id/approve', protect, requireAdmin, async (req, res) => {
  try {
    const payment = await Payment.findByPk(req.params.id, {
      include: [
        { model: SubscriptionPlan, as: 'plan' },
        { model: User },
      ],
    })

    if (!payment) return fail(res, 404, 'Payment not found')

    if (payment.status !== 'pending') {
      return fail(res, 400, `Payment is already ${payment.status}`)
    }

    await payment.update({
      status: 'approved',
      approved_at: new Date(),
    })

    const today = new Date()
    const endDate = new Date()
    endDate.setDate(endDate.getDate() + 30)

    await cancelOtherActiveSubscriptions(payment.user_id, payment.subscription_id)

    await Subscription.update(
      {
        status: 'active',
        start_date: today,
        end_date: endDate,
      },
      { where: { id: payment.subscription_id } }
    )

    if (payment.User) {
      await notifyUser(
        payment.user_id,
        'Payment approved',
        `Your ${payment.plan.name} plan is now active.`,
        'payment',
      )
      await sendEmail(
        payment.User.email,
        'Your SLAM subscription is active',
        `Hi ${payment.User.name},\n\n` +
        `Your payment was approved. The ${payment.plan.name} plan is now active.\n\n` +
        `Plan: ${payment.plan.name}\n` +
        `Amount: Rs. ${payment.amount_pkr}\n` +
        `Valid until: ${endDate.toDateString()}\n\n` +
        `Open the SLAM app to use your plan.\n`
      )
    }

    return ok(res, 'Payment approved and subscription activated', {
      payment_id: payment.id,
      subscription_id: payment.subscription_id,
      status: 'approved',
    })
  } catch (err) {
    console.error('Approve payment error:', err)
    return fail(res, 500, 'Unable to approve payment')
  }
})

router.patch('/admin/payments/:id/reject', protect, requireAdmin, async (req, res) => {
  try {
    const payment = await Payment.findByPk(req.params.id, {
      include: [{ model: User }],
    })

    if (!payment) return fail(res, 404, 'Payment not found')

    await payment.update({ status: 'rejected' })

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
        `Hi ${payment.User.name},\n\n` +
        `We could not verify the payment with transaction ID ${payment.transaction_id}.\n\n` +
        `Check the ID and submit again, or contact support if this looks wrong.\n`
      )
    }

    return ok(res, 'Payment rejected', {
      payment_id: payment.id,
      status: 'rejected',
    })
  } catch (err) {
    console.error('Reject payment error:', err)
    return fail(res, 500, 'Unable to reject payment')
  }
})

module.exports = router
