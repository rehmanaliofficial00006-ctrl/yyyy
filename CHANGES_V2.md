# V2 changes

- New Material 3 UI: Dashboard / History tabs, server status, connection card, copy buttons, light + dark theme.
- SIM selection: Auto / SIM 1 / SIM 2 (app setting), plus optional `"sim"` field per API request.
- Editable rate limit inside the app (per minute / per hour, or switch off = unlimited).
- Removed permissions: READ_CONTACTS, READ_SMS, RECEIVE_SMS. Added READ_PHONE_STATE (detect SIMs) and POST_NOTIFICATIONS.
- Fixes: bulk requests now count every message toward the limit; /api/history no longer hangs;
  SMS_SENT / SMS_DELIVERED broadcasts now target the app package (status updates work on modern Android);
  constant-time API key compare; key no longer written to logs; backups disabled.
