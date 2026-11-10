# ASL Wallet · Add Money (PayU) demo log

UAT debug build. The ASL server answers here are a stand-in that follows the backend spec; the PayU screens are PayU's test environment, or a simulated screen when no PayU test key is set. No real money moves.

### 12:41:55.408 · GET merchant/my-wallet/add-money

Request
```http
GET https://www.testproject.co.in/app/api/v1/merchant/my-wallet/add-money
Accept: application/json
X-Platform: android
X-App-Version: 2.0.0-m6-uat
X-Request-Id: 6287b416-f931-45c5-a00d-4ecc1470a432
Authorization: Bearer [hidden]
```

Response
```http
HTTP 200
X-ASL-Source: UAT server

{"status":true,"statusCode":200,"message":"Add money data fetched successfully","data":{"merchant":{"regno":6044,"fname":"Ajay","phone":"97XXXXXX77","email":"a•••@gmail.com"},"wallet":{"balanceAmt":"4027.00","balance_amt":"4027.00","rawBalance":4027},"limits":{"minAmount":1,"maxAmount":2000},"paymentModes":["PAYU","UPI","CREDIT_CARD","DEBIT_CARD","NET_BANKING"],"title":"Add Money"},"timestamp":"Monday, 09 November 2026, 12:41:55 PM"}
```

### 12:42:03.262 · POST payments/payu/initiate

Request
```http
POST https://www.testproject.co.in/api/v1/payments/payu/initiate
Idempotency-Key: ecc296c9-994a-4175-aabe-7f9505e7313c
Accept: application/json
X-Platform: android
X-App-Version: 2.0.0-m6-uat
X-Request-Id: 67954d87-3874-4004-bad6-4dfd4511754a
Authorization: Bearer [hidden]
Content-Type: application/json; charset=utf-8

{"amount":1,"paymentPurpose":"ASL Wallet Topup","isAndroid":true}
```

Response
```http
HTTP 200
X-ASL-Source: UAT server

{"status":true,"statusCode":200,"message":"Payment initiated successfully","data":{"clientTxnId":"PAYU6044_200147455500","txnid":"ASL6044T200147455500","amount":"1.00","currency":"INR","key":"1AJhSD","productinfo":"ASL Wallet Topup","firstname":"Ajay","email":"a•••@gmail.com","phone":"97XXXXXX77","hash":"be30534304d9f89a794c41d4055d42b9e5233e6fcad1e70e31c4eeef390af1a9ab5beae29bd96ec47b4a17a9609b2e1f1cb238cd774004254b66852e504df4bc","surl":"https://www.testproject.co.in/api/v1/payments/payu/success","furl":"https://www.testproject.co.in/api/v1/payments/payu/failure","isProduction":true,"idempotencyKey":"ecc296c9-994a-4175-aabe-7f9505e7313c"},"timestamp":"Monday, 09 November 2026, 12:42:03 PM"}
```

### 12:42:05.036 · SDK open

PayUCheckoutPro.open(isProduction=true, txnid=ASL6044T200147455500, amount=1.00)

### 12:42:05.066 · SDK generateHash

hashName=get_checkout_details hashType=null postSalt=null
hashString=1AJhSD|get_checkout_details|{"requestId":"ASL6044T2001474555001794208325062","transactionDetails":{"amount":1,"txnId":"ASL6044T200147455500","source":"Android_SDK"},"customerDetails":{"mobile":"97XXXXXX77"},"useCase":{"getAdditionalCharges":true,"getTaxSpecification":true,"checkDownStatus":true,"getExtendedPaymentDetails":true,"checkCustomerEligibility":true,"getMerchantDetails":true,"getPaymentDetailsWithExtraFields":true,"getSdkDetails":true},"isSITxn":false}|

### 12:42:05.084 · SDK generateHash

hashName=get_sdk_configuration hashType=null postSalt=null
hashString=1AJhSD|get_sdk_configuration|GET|

### 12:42:05.140 · SDK generateHash

hashName=get_all_offer_details hashType=null postSalt=null
hashString={"amount":1}|Mon, 09 Nov 2026 07:12:05 GMT|

### 12:42:05.189 · POST payments/payu/hash

Request
```http
POST https://www.testproject.co.in/api/v1/payments/payu/hash
Accept: application/json
X-Platform: android
X-App-Version: 2.0.0-m6-uat
X-Request-Id: 279b8d79-68f8-49db-81e5-6b9e5108d932
Authorization: Bearer [hidden]
Content-Type: application/json; charset=utf-8

{"txnid":"ASL6044T200147455500","hashName":"get_checkout_details","hashString":"1AJhSD|get_checkout_details|{\"requestId\":\"ASL6044T2001474555001794208325062\",\"transactionDetails\":{\"amount\":1,\"txnId\":\"ASL6044T200147455500\",\"source\":\"Android_SDK\"},\"customerDetails\":{\"mobile\":\"97XXXXXX77\"},\"useCase\":{\"getAdditionalCharges\":true,\"getTaxSpecification\":true,\"checkDownStatus\":true,\"getExtendedPaymentDetails\":true,\"checkCustomerEligibility\":true,\"getMerchantDetails\":true,\"getPaymentDetailsWithExtraFields\":true,\"getSdkDetails\":true},\"isSITxn\":false}|"}
```

Response
```http
HTTP 200
X-ASL-Source: UAT server

{"status":true,"statusCode":200,"message":"Hash generated successfully","data":{"hashName":"get_checkout_details","hash":"3359bce4bff41ca80d30b437f6b2fd6c2ffd2d37a384705ee4e5c2b0e206cfdf86792c03e2ffa1fee0895af1ee9992af985fa4c72361e3545ea0873184642c81"},"timestamp":"Monday, 09 November 2026, 12:42:05 PM"}
```

### 12:42:05.243 · POST payments/payu/hash

Request
```http
POST https://www.testproject.co.in/api/v1/payments/payu/hash
Accept: application/json
X-Platform: android
X-App-Version: 2.0.0-m6-uat
X-Request-Id: db98cbc7-321c-438a-96f8-f944aadbb7d4
Authorization: Bearer [hidden]
Content-Type: application/json; charset=utf-8

{"txnid":"ASL6044T200147455500","hashName":"get_sdk_configuration","hashString":"1AJhSD|get_sdk_configuration|GET|"}
```

Response
```http
HTTP 200
X-ASL-Source: UAT server

{"status":true,"statusCode":200,"message":"Hash generated successfully","data":{"hashName":"get_sdk_configuration","hash":"914bcc196b63bc3d9e1e4ee63a7d9e581f6f175ede574281c12ccdd267fc07f4162e2733843ef47a4481e9a7dd3ec00116a82c839c616e75b76dcac92a340795"},"timestamp":"Monday, 09 November 2026, 12:42:05 PM"}
```

### 12:42:05.296 · SDK generateHash

hashName=quickPayEvent hashType=null postSalt=null
hashString={"amount":1,"userToken":"","phone":"","requestId":"ASL6044T2001474555001794208325212","requestType":"quickpay","deviceId":"c12cb69401ca1fe2","userCredential":"1AJhSD:97XXXXXX77"}|Mon, 09 Nov 2026 07:12:05 GMT|

### 12:42:05.340 · POST payments/payu/hash

Request
```http
POST https://www.testproject.co.in/api/v1/payments/payu/hash
Accept: application/json
X-Platform: android
X-App-Version: 2.0.0-m6-uat
X-Request-Id: cf447111-e049-4e43-a04e-9c4de4a805b2
Authorization: Bearer [hidden]
Content-Type: application/json; charset=utf-8

{"txnid":"ASL6044T200147455500","hashName":"get_all_offer_details","hashString":"{\"amount\":1}|Mon, 09 Nov 2026 07:12:05 GMT|"}
```

Response
```http
HTTP 200
X-ASL-Source: UAT server

{"status":true,"statusCode":200,"message":"Hash generated successfully","data":{"hashName":"get_all_offer_details","hash":"c2a68052ddea016bcf725b8cffebfbc48aef7594b11496e8babcbe43b7be6c8349faac0dc5166f7b95c322965f335202d17633c0c6173c5906e1c64891adbe28"},"timestamp":"Monday, 09 November 2026, 12:42:05 PM"}
```

### 12:42:05.565 · POST payments/payu/hash

Request
```http
POST https://www.testproject.co.in/api/v1/payments/payu/hash
Accept: application/json
X-Platform: android
X-App-Version: 2.0.0-m6-uat
X-Request-Id: fe171fe7-30a6-4332-84c2-734bd2ffefcd
Authorization: Bearer [hidden]
Content-Type: application/json; charset=utf-8

{"txnid":"ASL6044T200147455500","hashName":"quickPayEvent","hashString":"{\"amount\":1,\"userToken\":\"\",\"phone\":\"\",\"requestId\":\"ASL6044T2001474555001794208325212\",\"requestType\":\"quickpay\",\"deviceId\":\"c12cb69401ca1fe2\",\"userCredential\":\"1AJhSD:97XXXXXX77\"}|Mon, 09 Nov 2026 07:12:05 GMT|"}
```

Response
```http
HTTP 200
X-ASL-Source: UAT server

{"status":true,"statusCode":200,"message":"Hash generated successfully","data":{"hashName":"quickPayEvent","hash":"7255ac89d993eca2df24634ed73551abd92f4095ac836650b03b5d142d1eb325de9998c5e32a4dc9aae5531d8a3bba3c8cd08c4fc54e3802ed0423af2f94aa84"},"timestamp":"Monday, 09 November 2026, 12:42:05 PM"}
```

### 12:42:06.605 · SDK generateHash

hashName=get_sdk_configuration hashType=null postSalt=null
hashString=1AJhSD|get_sdk_configuration|GET|

### 12:42:06.758 · POST payments/payu/hash

Request
```http
POST https://www.testproject.co.in/api/v1/payments/payu/hash
Accept: application/json
X-Platform: android
X-App-Version: 2.0.0-m6-uat
X-Request-Id: a2e8cb8c-a8d3-47d3-a527-423eb9c89cc3
Authorization: Bearer [hidden]
Content-Type: application/json; charset=utf-8

{"txnid":"ASL6044T200147455500","hashName":"get_sdk_configuration","hashString":"1AJhSD|get_sdk_configuration|GET|"}
```

Response
```http
HTTP 200
X-ASL-Source: UAT server

{"status":true,"statusCode":200,"message":"Hash generated successfully","data":{"hashName":"get_sdk_configuration","hash":"914bcc196b63bc3d9e1e4ee63a7d9e581f6f175ede574281c12ccdd267fc07f4162e2733843ef47a4481e9a7dd3ec00116a82c839c616e75b76dcac92a340795"},"timestamp":"Monday, 09 November 2026, 12:42:06 PM"}
```

### 12:42:06.957 · SDK generateHash

hashName=get_all_offer_details hashType=null postSalt=null
hashString={"amount":1}|Mon, 09 Nov 2026 07:12:06 GMT|

### 12:42:07.047 · POST payments/payu/hash

Request
```http
POST https://www.testproject.co.in/api/v1/payments/payu/hash
Accept: application/json
X-Platform: android
X-App-Version: 2.0.0-m6-uat
X-Request-Id: 6d81d44c-3bc7-44d1-9fbe-e74e0ffd1f66
Authorization: Bearer [hidden]
Content-Type: application/json; charset=utf-8

{"txnid":"ASL6044T200147455500","hashName":"get_all_offer_details","hashString":"{\"amount\":1}|Mon, 09 Nov 2026 07:12:06 GMT|"}
```

Response
```http
HTTP 200
X-ASL-Source: UAT server

{"status":true,"statusCode":200,"message":"Hash generated successfully","data":{"hashName":"get_all_offer_details","hash":"07cf7bf657f39329025f9946ee6cd22a40de57339852d5e7815fdd2dec061d878efe43585b866fefbdd0b4bc4a33bb647ab0610e2215a97f1ab5c2d280267e64"},"timestamp":"Monday, 09 November 2026, 12:42:07 PM"}
```

### 12:42:11.654 · SDK generateHash

hashName=payment_source hashType=null postSalt=null
hashString=1AJhSD|ASL6044T200147455500|1.00|ASL Wallet Topup|Ajay|a•••@gmail.com|||||||||||

### 12:42:11.732 · POST payments/payu/hash

Request
```http
POST https://www.testproject.co.in/api/v1/payments/payu/hash
Accept: application/json
X-Platform: android
X-App-Version: 2.0.0-m6-uat
X-Request-Id: ee4a7216-43f2-49cf-bd74-6e5f3a7dc00a
Authorization: Bearer [hidden]
Content-Type: application/json; charset=utf-8

{"txnid":"ASL6044T200147455500","hashName":"payment_source","hashString":"1AJhSD|ASL6044T200147455500|1.00|ASL Wallet Topup|Ajay|a•••@gmail.com|||||||||||"}
```

Response
```http
HTTP 200
X-ASL-Source: UAT server

{"status":true,"statusCode":200,"message":"Hash generated successfully","data":{"hashName":"payment_source","hash":"be30534304d9f89a794c41d4055d42b9e5233e6fcad1e70e31c4eeef390af1a9ab5beae29bd96ec47b4a17a9609b2e1f1cb238cd774004254b66852e504df4bc"},"timestamp":"Monday, 09 November 2026, 12:42:11 PM"}
```

### 12:43:00.953 · SDK onPaymentSuccess

payuResponse={"id":31749664891,"mode":"CASH","status":"success","unmappedstatus":"captured","key":"1AJhSD","txnid":"ASL6044T200147455500","transaction_fee":"1.00","amount":"1.00","discount":"0.00","addedon":"2026-11-09 12:42:12","productinfo":"ASL Wallet Topup","firstname":"Ajay","email":"a•••@gmail.com","phone":"97XXXXXX77","hash":"c9a88f9033de1d71ee82cdd8046096e51a12099f61db837712950802552579f8471d0cf5c14b3173dadb0b2b761bd0a705b3545c49bcadf403ea62a58e77d26b","field7":"VERPOSITIVE","field8":"Verification | success | ","field9":"Transaction Completed Successfully","payment_source":"payu","PG_TYPE":"CASH-PG","bank_ref_no":"2024504437","ibibo_code":"MOBIKWIK","error_code":"E000","Error_Message":"No Error","is_seamless":1,"surl":"https://www.testproject.co.in/api/v1/payments/payu/success","furl":"https://www.testproject.co.in/api/v1/payments/payu/failure"}
merchantResponse=null

### 12:43:01.101 · SDK generateHash

hashName=device_id hashType=null postSalt=null
hashString={"mihpayid":"31749664891","channel":"SDK","deviceInfo":{"gaId":"c12cb69401ca1fe2"}}|Mon, 09 Nov 2026 07:13:00 GMT|

### 12:43:01.237 · POST payments/payu/verify

Request
```http
POST https://www.testproject.co.in/api/v1/payments/payu/verify
Accept: application/json
X-Platform: android
X-App-Version: 2.0.0-m6-uat
X-Request-Id: 387fde82-81f5-46f4-94a2-4d19e0a6e326
Authorization: Bearer [hidden]
Content-Type: application/json; charset=utf-8

{"txnid":"ASL6044T200147455500"}
```

Response
```http
HTTP 200
X-ASL-Source: UAT server

{"status":true,"statusCode":200,"message":"Payment verification completed","data":{"txnid":"ASL6044T200147455500","status":"SUCCESS","amount":1,"closingBalance":4029,"credited":true,"message":"Transaction successfully processed and wallet credited"},"timestamp":"Monday, 09 November 2026, 12:43:01 PM"}
```

### 12:43:01.345 · POST payments/payu/hash

Request
```http
POST https://www.testproject.co.in/api/v1/payments/payu/hash
Accept: application/json
X-Platform: android
X-App-Version: 2.0.0-m6-uat
X-Request-Id: 864d5726-aae2-4287-8497-dcd24ec8c233
Authorization: Bearer [hidden]
Content-Type: application/json; charset=utf-8

{"txnid":"ASL6044T200147455500","hashName":"device_id","hashString":"{\"mihpayid\":\"31749664891\",\"channel\":\"SDK\",\"deviceInfo\":{\"gaId\":\"c12cb69401ca1fe2\"}}|Mon, 09 Nov 2026 07:13:00 GMT|"}
```

Response
```http
HTTP 200
X-ASL-Source: UAT server

{"status":true,"statusCode":200,"message":"Hash generated successfully","data":{"hashName":"device_id","hash":"b6b89da4b58710306af9473302e9c2d0b004f177e674531f013cd9d6ef896b5ef302b192168eb18bcb1f5b5369db37726a32f90f09aae067fdb75d2b82f833c1"},"timestamp":"Monday, 09 November 2026, 12:43:01 PM"}
```

### 12:43:01.712 · GET payments/payu/ASL6044T200147455500/status

Request
```http
GET https://www.testproject.co.in/api/v1/payments/payu/ASL6044T200147455500/status
Accept: application/json
X-Platform: android
X-App-Version: 2.0.0-m6-uat
X-Request-Id: 17f56fbb-cca6-4f16-97b1-47719ac8f9d7
Authorization: Bearer [hidden]
```

Response
```http
HTTP 200
X-ASL-Source: UAT server

{"status":true,"statusCode":200,"message":"Payment status retrieved","data":{"clientTxnId":"PAYU6044_200147455500","txnid":"ASL6044T200147455500","amount":1,"status":"SUCCESS","isPaymentComplete":true,"verifiedAt":"2026-11-09T07:12:03.415Z"},"timestamp":"Monday, 09 November 2026, 12:43:01 PM"}
```
