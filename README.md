# incorporation-information

### License  

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").

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
    "address_line_1": "Lane Road Drive",
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
        "address_line_1": "Lane Road Drive",
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



| Path                                                                | Supported Methods | Description |
| ------------------------------------------------------------------- | ----------------- | ----------- |
|```/incorporation-information/sic-codes/transaction/:transactionId```|       GET         | Fetches a companies sic codes using a transaction ID, this priorities Public information but defers to the pre-incorporation API if nothing is found
|```/incorporation-information/sic-codes/crn/:crn```                  |       GET         | Fetches an incorporated companies sic codes using its Company Number

Responds with:

| Status        | Message       |
|:--------------|:--------------|
| 200           | OK            |
| 204           | No Content    |

**Response body**

A ```200``` response:
```json
{
  "sic_codes": [
    "12345",
    "67890",
    "55555"
  ]
}
```

A ```204``` response: ```no body```


| Path                                                          | Supported Methods | Description |
| ------------------------------------------------------------- | ----------------- | ----------- |
|```/incorporation-information/shareholders/:txId```            |       GET         | Fetches a pre-incorporated companies shareholder list keyed on the transactionId

Responds with:

| Status        | Message       |
|:--------------|:--------------|
| 200           | OK            |
| 204           | No Content    |
| 404           | NotFound      |

A ```200``` response:
```json
[
  {
  "subscriber_type": "corporate",
    "name": "big company",
    "address": {
    "premises": "11",
    "address_line_1": " Add l 1",
    "address_line_2": "Add l 2",
    "locality": "London",
    "country": "United Kingdom",
    "postal_code": "postcode"
    },
  "percentage_voting_rights": 75.34
  }
  ]
```
# Running the App

To run the app you can run the service manager profile ```sm2 -start SCRS_ALL``` then run the```./run.sh``` script

# Testing the App

To run the tests for the application, you can run: ```sbt test or sbt it/test ```
