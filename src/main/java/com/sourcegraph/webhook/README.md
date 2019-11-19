# Webhooks
The Sourcegraph Bitbucket Server plugin provides an internal implementation for webhooks that supports listening to events at a global or project scope.

## Contents
- [Payload](#payload)
- [REST API](#rest-api)
    * [List](#list)
    * [Create](#create)
    * [Delete](#delete)

## Payload
The event payload request is sent when an event is fired. There will be five attempts before the request fails. This will be recorded in the logs.

Each request has the following headers:
- `X-Event-Key` - The name of the event
- `X-Hook-ID` - webhook id
- `X-Hook-Name` - webhook name
- `X-Hub-Signature` - HMAC SHA256 hash of the payload (request body)

The payload is stored in the request body as a JSON object. They follow the schema specified in [Event Payload](https://confluence.atlassian.com/bitbucketserver0516/event-payload-966061436.html?utm_campaign=in-app-help&utm_medium=in-app-help&utm_source=stash#Eventpayload-repositoryevents) and
[Bitbucket Entities](https://docs.atlassian.com/bitbucket-server/docs/5.16.0/reference/javascript/JSON.html) for each event.

Example (`pr:merged`):
<img width="1266" alt="Screen Shot 2019-11-13 at 6 21 57 AM" src="https://user-images.githubusercontent.com/3507526/68772049-fc7a3480-05dd-11ea-9676-707b40fd3daf.png">


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
* identifier - empty (`""`) if scope is global, otherwise, name of `project` or `repository`
* events - list of events; events can be: `pr`, `pr:opened`, `pr:modified`, `pr:reviewer`, `pr:reviewer:updated`, `pr:reviewer:approved`, `pr:reviewer:unapproved`, `pr:reviewer:needs_work`, `pr:merged`, `pr:declined`, `pr:deleted`, `pr:comment`, `pr:comment:added`, `pr:comment:edited`, `pr:comment:deleted`
* endpoint - url that the event payload will be sent to
* secret - key to sign payload request with

NOTE: More events can be added in the future. These are just the currently supported ones.

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
