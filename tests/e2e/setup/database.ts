import pg from 'pg';

const DATABASE_URL = process.env.DATABASE_URL || 'postgres://keycloak:keycloak@localhost:5432/keycloak';

class Database {
  private pool: pg.Pool | null = null;

  private getPool(): pg.Pool {
    if (!this.pool) {
      this.pool = new pg.Pool({
        connectionString: DATABASE_URL,
      });
    }
    return this.pool;
  }

  async clearTrustEntries(): Promise<void> {
    const pool = this.getPool();
    try {
      await pool.query('DELETE FROM email_otp_trusted_ip');
      await pool.query('DELETE FROM email_otp_trusted_device');
    } catch (error) {
      // Tables might not exist yet, ignore
      console.log('Trust tables may not exist yet:', error);
    }
  }

  async clearTrustEntriesForRealm(realmId: string): Promise<void> {
    const pool = this.getPool();
    try {
      await pool.query('DELETE FROM email_otp_trusted_ip WHERE realm_id = $1', [realmId]);
      await pool.query('DELETE FROM email_otp_trusted_device WHERE realm_id = $1', [realmId]);
    } catch (error) {
      console.log('Trust tables may not exist yet:', error);
    }
  }

  async close(): Promise<void> {
    if (this.pool) {
      await this.pool.end();
      this.pool = null;
    }
  }
}

export const database = new Database();
