# vspicy

VSpicy backend microservice system.

## Local MySQL access

All services read the database connection from `VSPICY_DB_URL` or from
`VSPICY_DB_HOST`, `VSPICY_DB_PORT`, `VSPICY_DB_NAME`, `VSPICY_DB_USERNAME`, and
`VSPICY_DB_PASSWORD`.

For local development, initialize the default database user with:

```powershell
cmd /c "mysql -uroot -p < scripts\dev\mysql-local-grants.sql"
```

The default application credentials are `vspicy` / `change_me`. If your local
MySQL uses a different password, set `VSPICY_DB_PASSWORD` before starting each
service.
