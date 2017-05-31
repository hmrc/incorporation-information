# incorporation-information

[![Build Status](https://travis-ci.org/hmrc/incorporation-information.svg)](https://travis-ci.org/hmrc/incorporation-information) [ ![Download](https://api.bintray.com/packages/hmrc/releases/incorporation-information/images/download.svg) ](https://bintray.com/hmrc/releases/incorporation-information/_latestVersion)

This is a placeholder README.md for a new repository 

### License  

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")

### API
 
| Path                                                                                      | Supported Methods | Description |
| ----------------------------------------------------------------------------------------- | ----------------- | ----------- |
|```/incorporation-information/subscribe/:transactionId/regime/:regime/subscriber/:sub```   |       POST        | Attempts to fetch incorporation data keyed on the transactionId and if it can't be found the service registers an interest in the incorporation with the transaction id provided and when retrieved, will fire the incorporation data to the provided callback url

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



| Path                                                             | Supported Methods | Description |
| ---------------------------------------------------------------- | ----------------- | ----------- |
|```/incorporation-information/:transactionId/company-profile```   |       GET         | Fetches a pre-incorporated companies company profile data keyed on the transactionId

Responds with:

| Status        | Message       |
|:--------------|:--------------|
| 200           | OK            |
| 404           | Not Found     |

**Response body**

A ```200``` response:
```json
{
  "company_number": "10371322",
  "transaction_id": "041-286558",
  "registered_office_address": {
    "country": "United Kingdom",
    "premises": "11",
    "postal_code": "ZZ4 3ZZ",
    "region": "Shropshire",
    "address_line_1": "Pear Drive",
    "locality": "TELFORD"
  },
  "company_name": "TEST COMPANY LIMITED",
  "company_type": "ltd",
  "sic_codes": [
    {
      "sic_description": "Manufacture of electric motors, generators and transformers",
      "sic_code": "27110"
    }
  ]
}
```

A ```404``` response: ```no body```
  

| Path                                                          | Supported Methods | Description |
| ------------------------------------------------------------- | ----------------- | ----------- |
|```/incorporation-information/:transactionId/officer-list```   |       GET         | Fetches a pre-incorporated companies officer list keyed on the transactionId

Responds with:

| Status        | Message       |
|:--------------|:--------------|
| 200           | OK            |
| 404           | Not Found     |

**Response body**

A ```200``` response:
```json
{
  "officers": [
    {
      "officer_role": "director",
      "date_of_birth": {
        "month": "12",
        "day": "12",
        "year": "1980"
      },
      "address": {
        "country": "United Kingdom",
        "premises": "11",
        "postal_code": "ZZ4 3ZZ",
        "region": "Shropshire",
        "address_line_1": "Pear Drive",
        "locality": "TELFORD"
      },
      "name_elements": {
        "forename": "John",
        "other_forenames": "Jim",
        "title": "Mr",
        "surname": "Johnson"
      }
    }
  ]
}
```

A ```404``` response: ```no body```
