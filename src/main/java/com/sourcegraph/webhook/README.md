# Webhooks
The Sourcegraph Bitbucket plugin provides an internal implementation for webhooks that supports listening to events at a global or project scope.

## Contents
- [Payload](#payload)
- [REST API](#rest-api)
    * [List](#list)
    * [Create](#create)
    * [Delete](#delete)

## Payload


## REST API
Interacting with the webhook REST endpoints requires [authentication](https://developer.atlassian.com/server/bitbucket/how-tos/example-basic-authentication/) from a system admin account.

#### List
```
$ curl -X GET 'https://${BITBUCKET_SERVER_URL}/rest/sourcegraph-admin/1.0/webhook'
```
There will be a 200 response code if successful.

Output: JSON serialized `[]Webhook`  
Ex: `[{"id":1,"name":"test","scope":"global","identifier":"","events":["pr"],"endpoint":"endpoint","secret":"secret"}]`

#### Create
```
curl -X POST 'https://${BITBUCKET_SERVER_URL}/rest/sourcegraph-admin/1.0/webhook' \
    -H 'Content-Type: application/json' \
    -d '{"name":"", "scope":"", "identifier":"", "events":["", "", ...], "endpoint":"", "secret":""}'
```
There will be a 204 response code if successful.  

**JSON Fields:**
* name - string id for webhook
* scope - can be either `global`, `project`, or `repository`
* identifier - empty if scope is global, otherwise, name of `project` or `repository`
* events - list of events; events can be: `pr`, `pr:opened`, `pr:modified`, `pr:reviewer`, `pr:reviewer:updated`, `pr:reviewer:approved`, `pr:reviewer:unapproved`, `pr:reviewer:needs_work`, `pr:merged`, `pr:declined`, `pr:deleted`, `pr:comment`, `pr:comment:added`, `pr:comment:edited`, `pr:comment:deleted`
* endpoint - url that the event payload will be sent to
* secret - key to sign payload request with

#### Delete
```
curl -X DELETE http://${BITBUCKET_SERVER_URL}/rest/sourcegraph-admin/1.0/webhook \
    -d 'name=<name>'
```
```
curl -X DELETE http://${BITBUCKET_SERVER_URL}/rest/sourcegraph-admin/1.0/webhook \
    -d 'id=<id>'
```
There will be a 204 response code if successful.
