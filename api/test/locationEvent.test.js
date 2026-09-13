const test = require('node:test')
const assert = require('node:assert/strict')
const { validateLocationEvent, usagePayload } = require('../utils/locationEvent')

const validBody = {
  event_id: 'b3f1f46e-9d3c-4c6d-8ed5-8d560b598274',
  latitude: 33.6844,
  longitude: 73.0479,
  requested_by: '03009876543',
  source: 'current',
  provider: 'fused',
  captured_at: '2026-09-11T12:30:00.000Z',
  accuracy_meters: 8.5,
}

test('normalizes a valid current location event', () => {
  const result = validateLocationEvent(validBody)

  assert.equal(result.error, undefined)
  assert.equal(result.value.source, 'CURRENT')
  assert.equal(result.value.requested_by, 'sms:***6543')
  assert.equal(result.value.accuracy_meters, 8.5)
  assert.equal(result.value.captured_at.toISOString(), validBody.captured_at)
})

test('accepts numeric accuracy from new clients and legacy accuracy labels', () => {
  const numeric = validateLocationEvent({ ...validBody, accuracy_meters: undefined, accuracy: 12.25 })
  const legacy = validateLocationEvent({ ...validBody, accuracy_meters: undefined, accuracy: 'HIGH' })

  assert.equal(numeric.value.accuracy_meters, 12.25)
  assert.equal(legacy.value.accuracy, 'HIGH')
})

test('rejects invalid UUID, source, and coordinate ranges', () => {
  assert.match(
    validateLocationEvent({ ...validBody, event_id: 'not-a-uuid' }).error,
    /event_id/
  )
  assert.match(
    validateLocationEvent({ ...validBody, source: 'cache' }).error,
    /source/
  )
  assert.match(
    validateLocationEvent({ ...validBody, latitude: 91 }).error,
    /latitude/
  )
  assert.match(
    validateLocationEvent({ ...validBody, longitude: Infinity }).error,
    /finite/
  )
})

test('idempotent response reports existing event without adding usage', () => {
  const event = { id: 7, event_id: validBody.event_id }
  const result = usagePayload(event, 3, 5, true)

  assert.equal(result.event, event)
  assert.equal(result.idempotent, true)
  assert.equal(result.requests_used, 3)
  assert.equal(result.requests_remaining, 2)
})
