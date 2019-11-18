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
Output: JSON serialized `[]Webhook`  
Ex: `[{"id":1,"name":"test","scope":"global","identifier":"","events":["pr"],"endpoint":"endpoint","secret":"secret"}]`

#### Create

#### Delete

