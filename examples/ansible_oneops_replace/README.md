# Ansible and OneOps replace event

## Prerequisites 

- concord.yml should be updated to the actual values of
`cfg.apiToken` and `cfg.secretPwd`; 

- the example must be added as a project to Concord. The `run.sh`
script uses the `concordTriggerExample` project;

- the target server's SSH key pair must be added to Concord as a
password-protected secret:
  ```
  curl -u MY_AD_USERNAME \
  -F private=@/path/to/mykey \
  -F public=@/path/to/mykey.pub \
  -F name=concordTriggerExampleKey \
  -F storePassword=q1q1q1q1 \
  -F type=KEY_PAIR \
  http://localhost:8001/api/v1/org/Default/secret
  ```