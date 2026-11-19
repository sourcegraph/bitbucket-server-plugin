# Webhooks

The Sourcegraph Bitbucket Server plugin provides an implementation for webhooks that supports listening to events at a global, project, or repository scope. To send them to Sourcegraph, follow [Incoming webhooks](https://sourcegraph.com/docs/admin/webhooks/incoming#bitbucket-server).

A global webhook receives events from every repository. A project-scoped webhook receives only events from repositories in that project, and a repository-scoped webhook only events from that repository. This applies to every event: pushes and build statuses use the repository they happened in, and pull request events use the pull request's target repository.

## Contents

- [Events](#events)
- [Payload](#payload)
- [REST API](#rest-api)
    * [List](#list)
    * [Create](#create)
    * [Delete](#delete)

## Events

The plugin sends these event keys in the `X-Event-Key` header:

| Event key | Sent for | Used by Sourcegraph |
| --- | --- | --- |
| `repo:refs_changed` | Pushes | Repository updates |
| `repo:build_status` | Build statuses set on a commit | Batch Changes |
| `pr:activity:event` | Pull request activity without a more specific key below, such as opening, reviews, or declining | Batch Changes |
| `pr:activity:rescope` | New commits on a pull request | Batch Changes |
| `pr:activity:merge` | Merged pull requests | Batch Changes |
| `pr:activity:comment` | Pull request comments | Batch Changes |
| `pr:activity:reviewers` | Reviewer changes | Batch Changes |
| `pr:participant:status` | A reviewer moving from Needs work to Unapproved, which has no activity event | Batch Changes |
| `pr:event` | Other pull request events | No |

A webhook subscribes to an event key or to any prefix of it: `pr:activity` matches every `pr:activity:*` key, and `repo` matches both `repo:*` keys. Avoid subscribing a Sourcegraph endpoint to `pr`, which also sends `pr:event`; Sourcegraph rejects that key.

## Payload

The event payload HTTP POST request is sent when an event is fired. In the case that the request fails, it is retried up to four more times, after 20 seconds, 1 minute, 2 minutes, and 5 minutes. The outcomes of these requests are recorded in the logs and the last error is shown on the plugin's admin page.

Each request has the following headers:

- `X-Event-Key` - The name of the event
- `X-Hook-ID` - The ID that represents the webhook
- `X-Hook-Name` - The name of the webhook that sends the request
- `X-Hub-Signature` - HMAC SHA256 hash of the payload (request body)
- `X-Attempt-Number` - The delivery attempt, starting at 1

The payload is sent in the request body as a JSON object. Each payload follows the schema specified in [Event Payload](https://confluence.atlassian.com/bitbucketserver0516/event-payload-966061436.html?utm_campaign=in-app-help&utm_medium=in-app-help&utm_source=stash#Eventpayload-repositoryevents) and [Bitbucket Entities](https://docs.atlassian.com/bitbucket-server/docs/5.16.0/reference/javascript/JSON.html).

Here is an example payload from an earlier plugin version, sent when a pull request was opened (the plugin now sends this as `pr:activity:event`):

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

Example payload for `repo:refs_changed`:

```json
{
  "createdDate": 1717777064737,
  "user": {
    "name": "admin",
    "emailAddress": "admin@example.com",
    "active": true,
    "displayName": "Administrator",
    "id": 2,
    "slug": "admin",
    "type": "NORMAL",
    "links": { "self": [{ "href": "http://localhost:7990/users/admin" }] },
    "avatarUrl": "https://secure.gravatar.com/avatar/e64c7d89f26bd1972efa854d13d7dd61.jpg?s\u003d64\u0026d\u003dmm"
  },
  "repository": {
    "slug": "rep_1",
    "id": 1,
    "name": "rep_1",
    "hierarchyId": "2bba4665b20d5005be17",
    "scmId": "git",
    "state": "AVAILABLE",
    "statusMessage": "Available",
    "forkable": true,
    "project": {
      "key": "PROJECT_1",
      "id": 1,
      "name": "Project 1",
      "description": "Default configuration project #1",
      "public": false,
      "type": "NORMAL",
      "links": {
        "self": [{ "href": "http://localhost:7990/projects/PROJECT_1" }]
      },
      "avatarUrl": "/projects/PROJECT_1/avatar.png?s\u003d64\u0026v\u003d1681176287106"
    },
    "public": false,
    "archived": false,
    "links": {
      "clone": [
        {
          "href": "http://localhost:7990/scm/project_1/rep_1.git",
          "name": "http"
        },
        {
          "href": "ssh://git@localhost:7999/project_1/rep_1.git",
          "name": "ssh"
        }
      ],
      "self": [
        {
          "href": "http://localhost:7990/projects/PROJECT_1/repos/rep_1/browse"
        }
      ]
    }
  },
  "refChanges": [
    {
      "ref": {
        "id": "refs/heads/master",
        "displayId": "master",
        "type": "BRANCH"
      },
      "refId": "refs/heads/master",
      "fromHash": "e54a46d4e4ddb7bb370070aa8a9b68e0ed959e5b",
      "toHash": "b14f55bd2b206c1128676131f9d66c56ec19e388",
      "type": "UPDATE"
    }
  ]
}
```

## REST API

Interacting with the webhook REST endpoints requires [authentication](https://developer.atlassian.com/server/bitbucket/how-tos/example-basic-authentication/) from a system admin account. Otherwise, requests will result in `401`s.

### List

```bash
$ curl -X GET 'https://${BITBUCKET_SERVER_URL}/rest/sourcegraph-admin/1.0/webhook'
```

There will be a 200 response code if successful.

Output: JSON serialized `[]Webhook`  

```json
[{
    "id": "1",
    "name": "webhook",
    "scope": "global",
    "events": ["repo:refs_changed", "pr:activity"],
    "endpoint": "https://${SOURCEGRAPH_URL}/.api/webhooks/54104dbb-0b71-4a83-aa6b-eef46241614a",
    "secret": "secret"
}]
```

### Create

```bash
$ curl -X POST 'https://${BITBUCKET_SERVER_URL}/rest/sourcegraph-admin/1.0/webhook' \
    -H 'Content-Type: application/json' \
    -d '{"name":"sourcegraph-webhook", "scope":"global", "events":["repo:refs_changed","repo:build_status","pr:activity","pr:participant:status"], "endpoint":"https://sourcegraph.example.com/.api/webhooks/54104dbb-0b71-4a83-aa6b-eef46241614a", "secret":"verylongsecret"}'
```

There will be a 204 response code if successful.  

**JSON Fields:**

- name - string id for webhook
- scope - can be either `global`, `project:<project name>`, or `repository:<project key>/<repository slug>`
- events - list of [event keys](#events) or prefixes of them
- endpoint - url that the event payload will be sent to
- secret - key to sign payload request with

Error codes:
`404` - No such project or repository  
`422` - Invalid scope

### Delete

```bash
$ curl -X DELETE http://${BITBUCKET_SERVER_URL}/rest/sourcegraph-admin/1.0/webhook \
    -d 'name=<name>'
```

```bash
$ curl -X DELETE http://${BITBUCKET_SERVER_URL}/rest/sourcegraph-admin/1.0/webhook \
    -d 'id=<id>'
```

There will be a 204 response code if successful.
