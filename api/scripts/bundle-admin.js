/**
 * Builds AdminJS custom components (Login, Dashboard) into .adminjs/bundle.js.
 * Run after changing api/admin/*.jsx, or automatically from app start when missing.
 */
require('dotenv').config()
process.env.ADMINJS_SKIP_WATCH = '1'

async function main() {
  const path = require('path')
  const bundlePath = path.join(__dirname, '..', '.adminjs', 'bundle.js')

  const { admin } = require('../admin')
  const {
    componentsBundler,
    generateUserComponentEntry,
    ADMIN_JS_TMP_DIR,
  } = await import('adminjs/bundler')

  const entry = generateUserComponentEntry(admin, ADMIN_JS_TMP_DIR)
  if (!entry.includes('Login') || !entry.includes('SlamDashboard')) {
    throw new Error('Admin component entry missing Login or SlamDashboard')
  }

  await componentsBundler.createEntry({ content: entry })
  await componentsBundler.build()
  const out = await componentsBundler.getOutput()
  if (!out || out.length < 1000) {
    throw new Error('Admin component bundle is empty')
  }
  console.log(`Admin components bundled (${out.length} bytes) → ${bundlePath}`)
}

main()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error('Admin bundle failed:', err)
    process.exit(1)
  })
