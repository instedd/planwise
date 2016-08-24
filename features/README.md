# Integration tests

## Setup

`up.sh` will start the following services and resources to run test with capybara

* a _planwise_ network where all containers will be able to see each other.
* a _smtp_ fake server where emails sent can be viewed at [http://smtp-local.instedd.org](http://smtp-local.instedd.org)
* a _guisso_ at [http://guisso-local.instedd.org](http://guisso-local.instedd.org)
* a _resourcemap_ at [http://resmap-local.instedd.org](http://resmap-local.instedd.org)
* a _planwise_ at [http://planwise-local.instedd.org](http://planwise-local.instedd.org)
* proxy that will allow the docker host to use the *-local.instedd.org url if the following lines are added to your `/etc/hosts`

```
127.0.0.1       guisso-local.instedd.org
127.0.0.1       resmap-local.instedd.org
127.0.0.1       planwise-local.instedd.org
127.0.0.1       smtp-local.instedd.org
```

* a _selenium_ environment which runs firefox. You are able to connect via vnc to `vnc://127.0.0.1:5900` (password: secret) it can be opened from safari or screen sharing app.
* a _ruby_ environment which loads all the `/spec` and will run the specs

## Teardown

`down.sh` will remove all data and state created by `up.sh`

`stop.sh`/`start.sh` will recreate all the containers but preserving state.

## Run specs

Connect to vnc if you want to view the progress.

```
$ docker-compose run --rm ruby bash
root@30c390cc90b0:/# cd /features
root@30c390cc90b0:/# rspec
```

An `admin@instedd.org`/`admin123` user exists after the setup.
