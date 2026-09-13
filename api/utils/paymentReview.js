const { sendEmail } = require('./email')
const { notifyUser } = require('./notify')
const { cancelOtherActiveSubscriptions } = require('./subscription')
const { Payment, Subscription, SubscriptionPlan, User } = require('../models')

async function loadPaymentForReview(paymentId) {
  return Payment.findByPk(paymentId, {
    include: [
      { model: SubscriptionPlan, as: 'plan' },
      { model: User },
    ],
  })
}

async function approvePayment(paymentId, reviewerEmail) {
  const payment = await loadPaymentForReview(paymentId)
  if (!payment) {
    const err = new Error('Payment not found')
    err.status = 404
    throw err
  }
  if (payment.status !== 'pending') {
    const err = new Error(`Payment is already ${payment.status}`)
    err.status = 400
    throw err
  }

  await payment.update({
    status: 'approved',
    approved_at: new Date(),
    reviewed_by: reviewerEmail || null,
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
      `Hi ${payment.User.name},\n\n` +
      `Your payment was approved. The ${payment.plan ? payment.plan.name : ''} plan is now active.\n\n` +
      `Plan: ${payment.plan ? payment.plan.name : ''}\n` +
      `Amount: Rs. ${payment.amount_pkr}\n` +
      `Valid until: ${endDate.toDateString()}\n\n` +
      `Open the SLAM app to use your plan.\n`
    )
  }

  return {
    payment_id: payment.id,
    subscription_id: payment.subscription_id,
    status: 'approved',
    reviewed_by: reviewerEmail || null,
  }
}

async function rejectPayment(paymentId, reviewerEmail) {
  const payment = await loadPaymentForReview(paymentId)
  if (!payment) {
    const err = new Error('Payment not found')
    err.status = 404
    throw err
  }
  if (payment.status !== 'pending') {
    const err = new Error(`Payment is already ${payment.status}`)
    err.status = 400
    throw err
  }

  await payment.update({
    status: 'rejected',
    reviewed_by: reviewerEmail || null,
  })

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

  return {
    payment_id: payment.id,
    status: 'rejected',
    reviewed_by: reviewerEmail || null,
  }
}

module.exports = { approvePayment, rejectPayment, loadPaymentForReview }
