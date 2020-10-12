apt-get update
apt-get install -y postgresql
/etc/init.d/postgresql start
su postgres -c "psql -c \"ALTER USER postgres PASSWORD 'q1';\""
