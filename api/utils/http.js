function ok(res, message, data = null, status = 200) {
  return res.status(status).json({ success: true, message, data })
}

function fail(res, status, message) {
  return res.status(status).json({ success: false, message, data: null })
}

module.exports = { ok, fail }
