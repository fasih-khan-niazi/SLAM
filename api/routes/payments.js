const express = require('express')
const multer = require('multer')
const { protect } = require('../middleware/auth')
const { sendEmailToAdmins } = require('../utils/email')
const { paymentSubmittedEmail } = require('../utils/emailTemplates')
const { isConfigured, uploadPaymentScreenshot } = require('../utils/cloudinary')
const { getSystemConfig } = require('../utils/config')
const { notifyAdmins, notifyUser } = require('../utils/notify')
const { ok, fail } = require('../utils/http')
const {
  Payment,
  Subscription,
  SubscriptionPlan,
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

    const openPayment = await Payment.findOne({
      where: { subscription_id: subscription.id, status: 'pending' },
    })
    if (openPayment) {
      return fail(res, 400, 'A payment for this plan is already waiting for admin review')
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

    const portalBase = (process.env.FRONTEND_URL || 'http://localhost:5173').replace(/\/$/, '')

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

    const mail = paymentSubmittedEmail({
      userName: req.user.name,
      userEmail: req.user.email,
      planName: subscription.plan.name,
      amountPkr: subscription.plan.price_pkr,
      paymentMethod: payment_method,
      transactionId: transaction_id,
      screenshotUrl,
      reviewUrl: `${portalBase}/admin/payments`,
    })
    await sendEmailToAdmins(mail.subject, mail.text, mail.html)

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

module.exports = router
