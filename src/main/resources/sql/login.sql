SELECT name, roles FROM t_account WHERE login_id = ${id} AND password = ${F:hash(password)}