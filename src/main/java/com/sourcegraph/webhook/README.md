# Webhooks
The Sourcegraph Bitbucket Server plugin provides an implementation for webhooks that supports listening to events at a global or project scope.

## Contents
- [Payload](#payload)
- [REST API](#rest-api)
    * [List](#list)
    * [Create](#create)
    * [Delete](#delete)

## Payload
The event payload HTTP POST request is sent when an event is fired. In the case that the request fails, there will be four further attempts with an interval of 10 seconds before it stops trying. The outcomes of these requests are recorded in the logs.

Each request has the following headers:
- `X-Event-Key` - The name of the event
- `X-Hook-ID` - The ID that represents the webhook
- `X-Hook-Name` - The name of the webhook that sends the request
- `X-Hub-Signature` - HMAC SHA256 hash of the payload (request body)

The payload is sent in the request body as a JSON object. Each payload follows the schema specified in [Event Payload](https://confluence.atlassian.com/bitbucketserver0516/event-payload-966061436.html?utm_campaign=in-app-help&utm_medium=in-app-help&utm_source=stash#Eventpayload-repositoryevents) and [Bitbucket Entities](https://docs.atlassian.com/bitbucket-server/docs/5.16.0/reference/javascript/JSON.html).


Here is an example payload for the event `pr:opened`:
```
2019/11/20 22:31:54 POST / HTTP/1.1                             
Host: localhost:4000                                            
Accept-Encoding: gzip,deflate                                   
Connection: Keep-Alive                                                                                                          
Content-Length: 2820                                                                                                            
Content-Type: text/plain; charset=UTF-8                                                                                         
User-Agent: Apache-HttpClient/4.5.5 (Java/1.8.0_144)                                                                            
X-Attempt-Number: 0                                             
X-Event-Key: pr:opened                                          
X-Hook-Id: 2                                                                                                                    
X-Hook-Name: cool                                                                                                               
X-Hub-Signature: c269feb28f9f317add3793f2b09e6125289ca12324dd45a85256e35dfaf5ee35                                               
                                                                                                                                
{"date":"2019-11-20T10:31:54-0800","actor":{"name":"admin","emailAddress":"admin@example.com","id":1,"displayName":"Administrato
r","active":true,"slug":"admin","type":"NORMAL","links":{"self":[{"href":"http://localhost:7990/bitbucket/users/admin"}]},"avata
rUrl":"https://secure.gravatar.com/avatar/e64c7d89f26bd1972efa854d13d7dd61.jpg?s\u003d64\u0026d\u003dmm"},"pullRequest":{"id":2,
"version":0,"title":"add_file.txt edited online with Bitbucket","description":"a","state":"OPEN","open":true,"closed":false,"cre
atedDate":1574317913743,"updatedDate":1574317913743,"fromRef":{"id":"refs/heads/admin/add_filetxt-1574317875130","displayId":"ad
min/add_filetxt-1574317875130","latestCommit":"2b9cff39d08fad011ab3607836988760b30f66e1","repository":{"slug":"rep_1","id":1,"na
me":"rep_1","scmId":"git","state":"AVAILABLE","statusMessage":"Available","forkable":true,"project":{"key":"PROJECT_1","id":1,"n
ame":"Project 1","description":"Default configuration project #1","public":false,"type":"NORMAL","links":{"self":[{"href":"http:
//localhost:7990/bitbucket/projects/PROJECT_1"}]},"avatarUrl":"/bitbucket/projects/PROJECT_1/avatar.png?s\u003d64\u0026v\u003d15
42341450256"},"public":false,"links":{"clone":[{"href":"http://localhost:7990/bitbucket/scm/project_1/rep_1.git","name":"http"},
{"href":"ssh://git@localhost:7999/project_1/rep_1.git","name":"ssh"}],"self":[{"href":"http://localhost:7990/bitbucket/projects/
PROJECT_1/repos/rep_1/browse"}]}}},"toRef":{"id":"refs/heads/master","displayId":"master","latestCommit":"0a943a29376f2336b78312
d99e65da17048951db","repository":{"slug":"rep_1","id":1,"name":"rep_1","scmId":"git","state":"AVAILABLE","statusMessage":"Availa
ble","forkable":true,"project":{"key":"PROJECT_1","id":1,"name":"Project 1","description":"Default configuration project #1","pu
blic":false,"type":"NORMAL","links":{"self":[{"href":"http://localhost:7990/bitbucket/projects/PROJECT_1"}]},"avatarUrl":"/bitbu
cket/projects/PROJECT_1/avatar.png?s\u003d64\u0026v\u003d1542341450256"},"public":false,"links":{"clone":[{"href":"http://localh
ost:7990/bitbucket/scm/project_1/rep_1.git","name":"http"},{"href":"ssh://git@localhost:7999/project_1/rep_1.git","name":"ssh"}]
,"self":[{"href":"http://localhost:7990/bitbucket/projects/PROJECT_1/repos/rep_1/browse"}]}}},"locked":false,"author":{"user":{"
name":"admin","emailAddress":"admin@example.com","id":1,"displayName":"Administrator","active":true,"slug":"admin","type":"NORMA
L","links":{"self":[{"href":"http://localhost:7990/bitbucket/users/admin"}]},"avatarUrl":"https://secure.gravatar.com/avatar/e64
c7d89f26bd1972efa854d13d7dd61.jpg?s\u003d64\u0026d\u003dmm"},"role":"AUTHOR","approved":false,"status":"UNAPPROVED"},"reviewers"
:[],"participants":[],"links":{"self":[{"href":"http://localhost:7990/bitbucket/projects/PROJECT_1/repos/rep_1/pull-requests/2"}
]}}}
```

## REST API
Interacting with the webhook REST endpoints requires [authentication](https://developer.atlassian.com/server/bitbucket/how-tos/example-basic-authentication/) from a system admin account. Otherwise, requests will result in `401`s.

#### List
```
$ curl -X GET 'https://${BITBUCKET_SERVER_URL}/rest/sourcegraph-admin/1.0/webhook'
```
There will be a 200 response code if successful.

Output: JSON serialized `[]Webhook`  
```
[{
    "id": "1",
    "name": "webhook",
    "scope": "global",
    "events": ["pr"],
    "endpoint": "https://${SOURCEGRAPH_URL}/.api/bitbucket-server-webhooks",
    "secret": "secret"
}]
```

#### Create
```
curl -X POST 'https://${BITBUCKET_SERVER_URL}/rest/sourcegraph-admin/1.0/webhook' \
    -H 'Content-Type: application/json' \
    -d '{"name":"sourcegraph-webhook", "scope":"global", "events":["pr"], "endpoint":"https://sourcegraph.example.com/.api/bitbucket-server-webhooks", "secret":"verylongsecret"}'
```
There will be a 204 response code if successful.  

**JSON Fields:**
* name - string id for webhook
* scope - can be either `global`, `project:<project name>`, or `repository:<project key>/<repository name>`
* events - list of events; events can be: `pr`, `pr:opened`, `pr:modified`, `pr:reviewer`, `pr:reviewer:updated`, `pr:reviewer:approved`, `pr:reviewer:unapproved`, `pr:reviewer:needs_work`, `pr:merged`, `pr:declined`, `pr:deleted`, `pr:comment`, `pr:comment:added`, `pr:comment:edited`, `pr:comment:deleted`
* endpoint - url that the event payload will be sent to
* secret - key to sign payload request with

Error codes:  
`404` - No such project or repository  
`422` - Invalid scope

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
