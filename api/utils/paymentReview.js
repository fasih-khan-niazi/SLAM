const { sendEmail } = require('./email')
const { paymentApprovedEmail, paymentRejectedEmail } = require('./emailTemplates')
const { notifyUser } = require('./notify')
const { cancelOtherActiveSubscriptions } = require('./subscription')
const { sequelize, Payment, Subscription, SubscriptionPlan, User } = require('../models')

async function loadPaymentForReview(paymentId) {
  return Payment.findByPk(paymentId, {
    include: [
      { model: SubscriptionPlan, as: 'plan' },
      { model: User },
    ],
  })
}

function reviewError(message, status) {
  const err = new Error(message)
  err.status = status
  return err
}

/** Loads the payment inside a transaction and locks it so two reviewers cannot both act on it. */
async function loadPendingPayment(paymentId, transaction) {
  // Lock only the payment row (MySQL cannot lock outer-joined rows), then load the joins.
  const locked = await Payment.findByPk(paymentId, { transaction, lock: transaction.LOCK.UPDATE })
  if (!locked) throw reviewError('Payment not found', 404)
  if (locked.status !== 'pending') throw reviewError('Payment is already ' + locked.status, 400)
  return Payment.findByPk(paymentId, {
    include: [
      { model: SubscriptionPlan, as: 'plan' },
      { model: User },
    ],
    transaction,
  })
}

async function approvePayment(paymentId, reviewerEmail) {
  // The payment, the old plan and the new plan change together or not at all.
  const payment = await sequelize.transaction(async (transaction) => {
    const pending = await loadPendingPayment(paymentId, transaction)

    const subscription = await Subscription.findByPk(pending.subscription_id, { transaction })
    if (!subscription || !['pending_payment', 'pending_approval'].includes(subscription.status)) {
      throw reviewError('This subscription is no longer waiting for payment', 400)
    }

    await pending.update(
      {
        status: 'approved',
        approved_at: new Date(),
        reviewed_by: reviewerEmail || null,
      },
      { transaction }
    )

    const today = new Date()
    const endDate = new Date()
    endDate.setDate(endDate.getDate() + 30)

    await cancelOtherActiveSubscriptions(pending.user_id, pending.subscription_id, transaction)
    await subscription.update(
      { status: 'active', start_date: today, end_date: endDate, requests_used: 0 },
      { transaction }
    )
    return pending
  })

  // Notices go out after the commit so a mail failure can never undo an approval.
  if (payment.User) {
    await notifyUser(
      payment.user_id,
      'Payment approved',
      `Your ${payment.plan ? payment.plan.name : ''} plan is now active.`,
      'payment',
    )
    const mail = paymentApprovedEmail({
      userName: payment.User.name,
      planName: payment.plan ? payment.plan.name : 'paid plan',
    })
    await sendEmail(payment.User.email, mail.subject, mail.text, mail.html)
  }

  return {
    payment_id: payment.id,
    subscription_id: payment.subscription_id,
    status: 'approved',
    reviewed_by: reviewerEmail || null,
  }
}

async function rejectPayment(paymentId, reviewerEmail) {
  const payment = await sequelize.transaction(async (transaction) => {
    const pending = await loadPendingPayment(paymentId, transaction)
    await pending.update(
      { status: 'rejected', reviewed_by: reviewerEmail || null },
      { transaction }
    )
    await Subscription.update(
      { status: 'pending_payment' },
      { where: { id: pending.subscription_id }, transaction }
    )
    return pending
  })

  if (payment.User) {
    await notifyUser(
      payment.user_id,
      'Payment rejected',
      `We could not verify transaction ${payment.transaction_id}.`,
      'payment',
    )
    const mail = paymentRejectedEmail({
      userName: payment.User.name,
      planName: payment.plan ? payment.plan.name : 'paid plan',
    })
    await sendEmail(payment.User.email, mail.subject, mail.text, mail.html)
  }

  return {
    payment_id: payment.id,
    status: 'rejected',
    reviewed_by: reviewerEmail || null,
  }
}

module.exports = { approvePayment, rejectPayment, loadPaymentForReview }
