# incorporation-information

[![Build Status](https://travis-ci.org/hmrc/incorporation-information.svg)](https://travis-ci.org/hmrc/incorporation-information) [ ![Download](https://api.bintray.com/packages/hmrc/releases/incorporation-information/images/download.svg) ](https://bintray.com/hmrc/releases/incorporation-information/_latestVersion)

This is a placeholder README.md for a new repository 

### License  

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")

### Test endpoints

| Feature switch test endpoint path                                                         | Supported Methods | Description |
| ----------------------------------------------------------------------------------------- | ----------------- | ----------- |
|```/incorporation-information/test-only/feature-switch/transactionalAPI/(stub or coho)```  |        GET        | Directs the calls to fetch incorporations and submission data requests to either incorporation-frontend-stubs (stub) or companies house (coho)   |         
|```/incorporation-information/test-only/feature-switch/incorpUpdate/(on or off)```         |        GET        | Turns the scheduler that polls for incorporations on or off   |         


| Manual trigger test endpoint path                                       | Supported Methods | Description |
| ----------------------------------------------------------------------- | ----------------- | ----------- |
|```/incorporation-information/test-only/manual-trigger/incorp-update```  |        GET        | manually triggers a poll for incorporations |                  