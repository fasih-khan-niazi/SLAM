import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import {
  adminCreatePlan,
  adminDeletePlan,
  adminListPlans,
  adminUpdatePlan,
} from '../../api/endpoints'
import { ApiError } from '../../api/client'
import { Banner } from '../../components/Banner'
import { Button } from '../../components/Button'
import { Field } from '../../components/Field'
import { Modal } from '../../components/Modal'
import { Skeleton } from '../../components/Skeleton'
import { StatusChip } from '../../components/StatusChip'

const emptyForm = {
  name: '',
  price_pkr: '0',
  monthly_limit: '5',
  max_contacts: '1',
  has_history: false,
  description: '',
  is_active: true,
  unlimited: false,
}

export function AdminPlansPage() {
  const { token } = useAuth()
  const [plans, setPlans] = useState(null)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState(emptyForm)
  const [busy, setBusy] = useState(false)
  const [deleteId, setDeleteId] = useState(null)

  const load = useCallback(() => {
    return adminListPlans(token)
      .then((res) => setPlans(res.data.plans || []))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Unable to load plans'))
  }, [token])

  useEffect(() => {
    load()
  }, [load])

  function openCreate() {
    setEditing('new')
    setForm(emptyForm)
  }

  function openEdit(plan) {
    setEditing(plan.id)
    setForm({
      name: plan.name || '',
      price_pkr: String(plan.price_pkr ?? 0),
      monthly_limit: plan.monthly_limit == null ? '' : String(plan.monthly_limit),
      max_contacts: String(plan.max_contacts ?? 1),
      has_history: Boolean(plan.has_history),
      description: plan.description || '',
      is_active: Boolean(plan.is_active),
      unlimited: plan.monthly_limit == null,
    })
  }

  async function save() {
    setBusy(true)
    setError(null)
    try {
      const body = {
        name: form.name,
        price_pkr: Number(form.price_pkr) || 0,
        monthly_limit: form.unlimited ? null : (form.monthly_limit === '' ? null : Number(form.monthly_limit)),
        max_contacts: Number(form.max_contacts) || 1,
        has_history: form.has_history,
        description: form.description,
        is_active: form.is_active,
      }
      if (editing === 'new') {
        await adminCreatePlan(token, body)
        setNotice('Plan created.')
      } else {
        await adminUpdatePlan(token, editing, body)
        setNotice('Plan updated.')
      }
      setEditing(null)
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to save plan')
    } finally {
      setBusy(false)
    }
  }

  async function remove() {
    if (!deleteId) return
    setBusy(true)
    try {
      await adminDeletePlan(token, deleteId)
      setNotice('Plan deleted.')
      setDeleteId(null)
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to delete plan')
      setDeleteId(null)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="admin-page">
      <p className="eyebrow">Product</p>
      <h1>Plans</h1>
      <p className="lede">Create and edit subscription tiers. Live entitlements follow the plan row.</p>

      <div className="admin-toolbar">
        <Button onClick={openCreate}>Add plan</Button>
      </div>

      {error ? <Banner tone="danger" title="Error" message={error} /> : null}
      {notice ? <Banner tone="success" title="Done" message={notice} /> : null}

      {plans == null ? (
        <Skeleton height={200} />
      ) : (
        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Price</th>
                <th>Limit</th>
                <th>Contacts</th>
                <th>History</th>
                <th>Active</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {plans.map((plan) => (
                <tr key={plan.id}>
                  <td>
                    <strong>{plan.name}</strong>
                    {plan.description ? (
                      <div className="muted" style={{ fontSize: '0.8rem' }}>{plan.description}</div>
                    ) : null}
                  </td>
                  <td>Rs {plan.price_pkr}</td>
                  <td>{plan.monthly_limit == null ? 'Unlimited' : plan.monthly_limit}</td>
                  <td>{plan.max_contacts}</td>
                  <td>{plan.has_history ? 'Yes' : 'No'}</td>
                  <td>
                    <StatusChip tone={plan.is_active ? 'success' : 'neutral'}>
                      {plan.is_active ? 'Active' : 'Off'}
                    </StatusChip>
                  </td>
                  <td>
                    <div className="stack" style={{ gap: 6 }}>
                      <Button variant="secondary" onClick={() => openEdit(plan)}>Edit</Button>
                      <Button variant="danger" onClick={() => setDeleteId(plan.id)}>Delete</Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editing != null ? (
        <Modal
          title={editing === 'new' ? 'New plan' : 'Edit plan'}
          confirmLabel={busy ? 'Saving…' : 'Save'}
          cancelLabel="Cancel"
          onConfirm={busy ? undefined : save}
          onDismiss={() => !busy && setEditing(null)}
        >
          <div className="stack-lg">
            <Field id="plan-name" label="Name" value={form.name} onChange={(v) => setForm({ ...form, name: v })} required />
            <Field id="plan-price" label="Price (PKR)" type="number" integerMode="nonNegative" min={0} value={form.price_pkr} onChange={(v) => setForm({ ...form, price_pkr: v })} />
            <label className="admin-toggle-row">
              <span>Unlimited locates</span>
              <input
                type="checkbox"
                checked={form.unlimited}
                onChange={(e) => setForm({ ...form, unlimited: e.target.checked })}
              />
            </label>
            {!form.unlimited ? (
              <Field
                id="plan-limit"
                label="Monthly limit"
                type="number"
                integerMode="positive"
                min={1}
                value={form.monthly_limit}
                onChange={(v) => setForm({ ...form, monthly_limit: v })}
              />
            ) : null}
            <Field
              id="plan-contacts"
              label="Max contacts"
              type="number"
              integerMode="positive"
              min={1}
              value={form.max_contacts}
              onChange={(v) => setForm({ ...form, max_contacts: v })}
            />
            <Field
              id="plan-desc"
              label="Description"
              value={form.description}
              onChange={(v) => setForm({ ...form, description: v })}
            />
            <label className="admin-toggle-row">
              <span>History enabled</span>
              <input
                type="checkbox"
                checked={form.has_history}
                onChange={(e) => setForm({ ...form, has_history: e.target.checked })}
              />
            </label>
            <label className="admin-toggle-row">
              <span>Active in catalog</span>
              <input
                type="checkbox"
                checked={form.is_active}
                onChange={(e) => setForm({ ...form, is_active: e.target.checked })}
              />
            </label>
          </div>
        </Modal>
      ) : null}

      {deleteId ? (
        <Modal
          title="Delete plan?"
          message="Only plans with no subscriptions or payments can be deleted. Prefer deactivating."
          confirmLabel="Delete"
          cancelLabel="Cancel"
          danger
          onConfirm={remove}
          onDismiss={() => setDeleteId(null)}
        />
      ) : null}
    </div>
  )
}
