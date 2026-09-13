const express = require('express')
const { ok, fail } = require('../utils/http')
const { getSystemConfig } = require('../utils/config')

const router = express.Router()

router.get('/config', async (req, res) => {
  try {
    const config = await getSystemConfig()
    return ok(res, 'Config fetched', config)
  } catch (err) {
    console.error('Config error:', err)
    return fail(res, 500, 'Unable to load config')
  }
})

module.exports = router
