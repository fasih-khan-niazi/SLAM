require('dotenv').config()
const sequelize = require('../config/database')

function isDuplicateName(name) {
  return /^(email|transaction_id)_\d+$/.test(name)
}

async function main() {
  await sequelize.authenticate()

  const [rows] = await sequelize.query(`
    SELECT TABLE_NAME, INDEX_NAME
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND INDEX_NAME REGEXP '^(email|transaction_id)_[0-9]+$'
    GROUP BY TABLE_NAME, INDEX_NAME
  `)

  if (!rows.length) {
    console.log('No duplicate unique indexes to drop')
    await sequelize.close()
    return
  }

  for (const row of rows) {
    if (!isDuplicateName(row.INDEX_NAME)) continue
    const sql = `ALTER TABLE \`${row.TABLE_NAME}\` DROP INDEX \`${row.INDEX_NAME}\``
    await sequelize.query(sql)
    console.log(`Dropped ${row.TABLE_NAME}.${row.INDEX_NAME}`)
  }

  const [after] = await sequelize.query(`
    SELECT TABLE_NAME AS tbl, COUNT(DISTINCT INDEX_NAME) AS index_count
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
    GROUP BY TABLE_NAME
    ORDER BY index_count DESC
  `)
  console.log(JSON.stringify(after, null, 2))
  await sequelize.close()
}

main().catch((err) => {
  console.error(err.message)
  process.exit(1)
})
