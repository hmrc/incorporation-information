# incorporation-information

[![Build Status](https://travis-ci.org/hmrc/incorporation-information.svg)](https://travis-ci.org/hmrc/incorporation-information) [ ![Download](https://api.bintray.com/packages/hmrc/releases/incorporation-information/images/download.svg) ](https://bintray.com/hmrc/releases/incorporation-information/_latestVersion)

This is a placeholder README.md for a new repository 

### License  

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")

### API

| Path                                                                                      | Supported Methods | Description |
| ----------------------------------------------------------------------------------------- | ----------------- | ----------- |
|```/incorporation-information/subscribe/:transactionId/regime/:regime/subscriber/:sub```   |       POST        | Registers an interest in an incorporation with the transaction id provided and when retrieved, will fire the incorporation data to the provided callback url

Responds with:

| Status        | Message       |
|:--------------|:--------------|
| 200           | OK            |
| 202           | Accepted      |

**Request body**

```json
{
  "SCRSIncorpSubscription": {
    "callbackUrl": "www.test.com"
  }
}
```

**Response body**

A ```200``` response:
```json
{
  "SCRSIncorpStatus":{
    "IncorpSubscriptionKey":{
      "subscriber":"SCRS",
      "discriminator":"PAYE",
      "transactionId":"123456789"
    },
    "SCRSIncorpSubscription":{
      "callbackUrl":"/callBackUrl"
    },
    "IncorpStatusEvent":{
      "status":"accepted|rejected",
      "crn":"123456789",
      "incorporationDate":"2017-04-25T16:20:10.000+01:00",
      "description":"Some description",
      "timestamp":"2017-04-25T16:20:10.000+01:00"
    }
  }
}
```

A ```202``` response: ```no body```

### Test endpoints

| Feature switch test endpoint path                                                         | Supported Methods | Description |
| ----------------------------------------------------------------------------------------- | ----------------- | ----------- |
|```/incorporation-information/test-only/feature-switch/transactionalAPI/(stub or coho)```  |        GET        | Directs the calls to fetch incorporations and submission data requests to either incorporation-frontend-stubs (stub) or companies house (coho)   |         
|```/incorporation-information/test-only/feature-switch/incorpUpdate/(on or off)```         |        GET        | Turns the scheduler that polls for incorporations on or off   |         


| Manual trigger test endpoint path                                       | Supported Methods | Description |
| ----------------------------------------------------------------------- | ----------------- | ----------- |
|```/incorporation-information/test-only/manual-trigger/incorp-update```  |        GET        | manually triggers a poll for incorporations |            
      
### How to use (for testing subscriptions with the stub)

__For a 200 response__
1\. Setup an incorporation in IFES 

>__POST /incorporation-frontend-stubs/test-only/insert-submission__

>```json
{
	"company_number":"90000001",
	"transaction_status":"accepted",
	"transaction_type":"incorporation",
	"company_profile_link":"https://api.companieshouse.gov.uk/company/90000001",
	"transaction_id":"92836405817",
	"incorporated_on":"2009-01-01",
	"timepoint":"22"
}
```

2\. point the transactional API feature switch to the stub

>__GET /incorporation-information/test-only/feature-switch/transactionalAPI/stub__

3\. enable the incorpUpdate scheduler (manually trigger if you don't want to wait)

>__GET /incorporation-information/test-only/feature-switch/incorpUpdate/on__

4\. hit the subscribe API, providing the same trans ID as the one in the incorporation setup

>__POST /subscribe/:transId/regime/:regime/subscriber/:sub__
> Where transId needs to be the same as the one on the test incorporation in IFES

>e.g. /subscribe/92836405817/regime/PAYE/subscriber/SCRS