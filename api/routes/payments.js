const express = require('express')
const multer = require('multer')
const path = require('path')
const fs = require('fs')
const { protect, requireAdmin } = require('../middleware/auth')
const { sendEmail } = require('../utils/email')
const {
  Payment,
  Subscription,
  SubscriptionPlan,
  User,
} = require('../models')

const router = express.Router()

const uploadsDir = path.join(__dirname, '..', 'uploads')
if (!fs.existsSync(uploadsDir)) {
  fs.mkdirSync(uploadsDir)
}

const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, uploadsDir),
  filename: (req, file, cb) => {
    const safe = file.originalname.replace(/[^a-zA-Z0-9.\-_]/g, '_')
    cb(null, `${Date.now()}-${safe}`)
  },
})

const upload = multer({
  storage,
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
    const screenshot = req.file ? req.file.filename : null

    if (!subscription_id || !payment_method || !transaction_id) {
      return res.status(400).json({
        success: false,
        message: 'subscription_id, payment_method and transaction_id are required',
        data: null,
      })
    }

    if (!['jazzcash', 'easypaisa'].includes(payment_method)) {
      return res.status(400).json({
        success: false,
        message: 'payment_method must be jazzcash or easypaisa',
        data: null,
      })
    }

    const duplicate = await Payment.findOne({ where: { transaction_id } })
    if (duplicate) {
      return res.status(400).json({
        success: false,
        message: 'This transaction ID has already been submitted',
        data: null,
      })
    }

    const subscription = await Subscription.findOne({
      where: { id: subscription_id, user_id },
      include: [{ model: SubscriptionPlan, as: 'plan' }],
    })

    if (!subscription) {
      return res.status(404).json({
        success: false,
        message: 'Subscription not found',
        data: null,
      })
    }

    const payment = await Payment.create({
      user_id,
      plan_id: subscription.plan_id,
      subscription_id: subscription.id,
      amount_pkr: subscription.plan.price_pkr,
      payment_method,
      transaction_id,
      screenshot_url: screenshot,
      status: 'pending',
    })

    await subscription.update({ status: 'pending_approval' })

    const adminInbox = process.env.ADMIN_EMAIL
    const publicApi = process.env.API_PUBLIC_URL || process.env.FRONTEND_URL || 'http://localhost:3000'

    if (adminInbox) {
      await sendEmail(
        adminInbox,
        'SLAM — new payment to review',
        `A payment was submitted and needs review.\n\n` +
        `User: ${req.user.name} (${req.user.email})\n` +
        `Plan: ${subscription.plan.name}\n` +
        `Amount: Rs. ${subscription.plan.price_pkr}\n` +
        `Method: ${payment_method}\n` +
        `Transaction ID: ${transaction_id}\n\n` +
        `Admin panel: ${publicApi}/admin`
      )
    }

    return res.status(201).json({
      success: true,
      message: 'Payment submitted. It will be reviewed shortly.',
      data: {
        payment_id: payment.id,
        status: payment.status,
        transaction_id: payment.transaction_id,
        payment_method: payment.payment_method,
      },
    })
  } catch (err) {
    console.error('Payment submit error:', err)
    return res.status(500).json({
      success: false,
      message: 'Unable to submit payment',
      data: null,
    })
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
      plan_name: p.plan.name,
      amount_pkr: p.amount_pkr,
      payment_method: p.payment_method,
      transaction_id: p.transaction_id,
      status: p.status,
      submitted_at: p.createdAt,
      approved_at: p.approved_at,
    }))

    return res.status(200).json({
      success: true,
      message: 'Payment history fetched',
      data: { payments: formatted },
    })
  } catch (err) {
    console.error('Get payments error:', err)
    return res.status(500).json({
      success: false,
      message: 'Unable to load payments',
      data: null,
    })
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

    if (!payment) {
      return res.status(404).json({
        success: false,
        message: 'Payment not found',
        data: null,
      })
    }

    if (payment.status !== 'pending') {
      return res.status(400).json({
        success: false,
        message: `Payment is already ${payment.status}`,
        data: null,
      })
    }

    await payment.update({
      status: 'approved',
      approved_at: new Date(),
    })

    const today = new Date()
    const endDate = new Date()
    endDate.setDate(endDate.getDate() + 30)

    await Subscription.update(
      {
        status: 'active',
        start_date: today,
        end_date: endDate,
      },
      { where: { id: payment.subscription_id } }
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

    return res.status(200).json({
      success: true,
      message: 'Payment approved and subscription activated',
      data: {
        payment_id: payment.id,
        subscription_id: payment.subscription_id,
        status: 'approved',
      },
    })
  } catch (err) {
    console.error('Approve payment error:', err)
    return res.status(500).json({
      success: false,
      message: 'Unable to approve payment',
      data: null,
    })
  }
})

router.patch('/admin/payments/:id/reject', protect, requireAdmin, async (req, res) => {
  try {
    const payment = await Payment.findByPk(req.params.id, {
      include: [{ model: User }],
    })

    if (!payment) {
      return res.status(404).json({
        success: false,
        message: 'Payment not found',
        data: null,
      })
    }

    await payment.update({ status: 'rejected' })

    await Subscription.update(
      { status: 'pending_payment' },
      { where: { id: payment.subscription_id } }
    )

    await sendEmail(
      payment.User.email,
      'SLAM payment could not be verified',
      `Hi ${payment.User.name},\n\n` +
      `We could not verify the payment with transaction ID ${payment.transaction_id}.\n\n` +
      `Check the ID and submit again, or contact support if this looks wrong.\n`
    )

    return res.status(200).json({
      success: true,
      message: 'Payment rejected',
      data: { payment_id: payment.id, status: 'rejected' },
    })
  } catch (err) {
    console.error('Reject payment error:', err)
    return res.status(500).json({
      success: false,
      message: 'Unable to reject payment',
      data: null,
    })
  }
})

module.exports = router
