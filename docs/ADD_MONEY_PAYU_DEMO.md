# Add Money with PayU · demo build and sample log

**For the backend team.** This is what the ASL Wallet app sends and expects for Add Money through the PayU SDK. Updated 9 Nov 2026 for the backend's collection "ASL PayU Checkout Pro API" (payments/payu/initiate, hash, verify, status, success, failure). The sample below shows each request and response in order. Build the real APIs to give the same responses.

Spec with every rule: https://claude.ai/artifact/ScaeNkCqnPvwt1Xhm7dw76

## How the demo works (UAT debug build only)

- The app's real Add Money code runs unchanged. Add Money's balance call and the `payments/payu/…` calls are answered by a stand-in inside the UAT debug build until they are on UAT (`app/src/uatDebug/.../demo/`). Each response has the header `X-ASL-Demo: true`. Every other API goes to the real UAT server.
- **PayU screens:** if `local.properties` has a PayU **test** key and salt, the real PayU test screens open, and the status check calls PayU's test `verify_payment`. Without them, a simulated PayU screen lets you choose Pay successfully, Payment fails or Cancel.
  ```
  payu.test.key=<your PayU test key>
  payu.test.salt=<your PayU test salt>
  ```
  `local.properties` is never committed. Only UAT debug builds get these values. ASL's real PayU key and salt are never in the app.
- **No real money moves,** and the real wallet balance doesn't change. The demo uses a made-up retailer ("Demo Retailer", demo.retailer@example.com, 9999999999).
- **The log:** every request and response is recorded exactly, with only the `Authorization` token hidden, together with what the PayU SDK asked for and what the server must do at each step. Share it by long-pressing the app icon → **Share demo log**, or from the notification if notifications are allowed. It's also in Chucker and in Logcat (tag `AslAddMoneyDemo`). Opening Add Money starts a new log.

## Sample (initiate → hash → PayU success → verify → status)

```http
GET /api/v1/merchant/my-wallet/add-money
Authorization: Bearer <token>
```
```http
HTTP 200
Content-Type: application/json

{
  "status": true,
  "message": "Add money data fetched successfully",
  "data": {
    "wallet": {
      "balanceAmt": "1022.00"
    },
    "limits": {
      "minAmount": 1000,
      "maxAmount": 10000
    },
    "paymentModes": [
      "PAYU"
    ],
    "charges": {
      "type": "PERCENT",
      "value": "0.00",
      "note": "No charges"
    }
  }
}
```

> **Server: order created** · Saved ASLD2611070250006408 as INITIATED for ₹1500.00. Payment hash = sha512(key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5||||||SALT).

```http
POST /api/v1/payments/payu/initiate
Authorization: Bearer <token>
Idempotency-Key: 3f1c9a52-6a0e-4b7e-9f1d-2c2b8e7a41d0
Content-Type: application/json

{"amount":1500,"paymentPurpose":"ASL Wallet Topup","isAndroid":true}
```
```http
HTTP 201
Content-Type: application/json

{
  "status": true,
  "message": "Order created",
  "data": {
    "txnid": "ASLD2611070250006408",
    "clientTxnId": "ASLD2611070250006408",
    "amount": "1500.00",
    "status": "PENDING",
    "expiresAt": "2026-11-07T03:20:00+05:30",
    "payu": {
      "key": "DEMO_NO_PAYU_KEY",
      "txnid": "ASLD2611070250006408",
      "amount": "1500.00",
      "productinfo": "ASL Wallet top-up",
      "firstname": "Demo Retailer",
      "email": "demo.retailer@example.com",
      "phone": "9999999999",
      "surl": "https://www.testproject.co.in/app/api/v1/payments/payu/success",
      "furl": "https://www.testproject.co.in/app/api/v1/payments/payu/failure",
      "udf1": "100001",
      "udf2": "",
      "udf3": "",
      "udf4": "",
      "udf5": "",
      "hash": "bdb857659121b35532dd4d22610d13e9e386c1c7e948dedb9d6b04ccecd94d7a73f3598ae55368fb3ff4ef8bcc60a0d3fd385b9cf88bc19178e03736f22c0aae",
      "payment_related_details_for_mobile_sdk": "deb86c11fd3803b0f979b0713cf42ee173a5e7a2d344cac6f3ea7a752ac1e41b0b13b70be623d4e7a2d2eb34a28b28106cb5ce29f60488d69f86e40dfad39f9e",
      "vas_for_mobile_sdk": "9fa1bad3fa908c0265bb09db8d0e322d1cb648351c06a53b08a35e7ff6a8cf4516e3389931be60b2eb88e5cd7b32567297fe5ea71902966b964b2c71dbb7c28b",
      "environment": "TEST"
    }
  }
}
```

> **Server: hash made** · get_eligible_payment_options (SHA-512)

```http
POST /api/v1/payments/payu/hash
Authorization: Bearer <token>
Content-Type: application/json

{"txnid":"ASLD2611070250006408","hashName":"get_eligible_payment_options","hashString":"DEMO_NO_PAYU_KEY|get_eligible_payment_options|{\"amount\":\"1500.00\",\"txnid\":\"ASLD2611070250006408\"}|","postSalt":null,"hashType":null}
```
```http
HTTP 200
Content-Type: application/json

{
  "status": true,
  "message": "Hash generated",
  "data": {
    "hash": "ec984aff00a9c60f7ccdfbd1ad0b45b867ddfee541a6f12773e0ab1ab1ff47ce18d9024c621c3f0309e0b7f06538566c6546bf2a8d1206bb3633978a737c68f7"
  }
}
```

```http
POST /api/v1/payments/payu/success   (PayU → server)
Content-Type: application/x-www-form-urlencoded

mihpayid=403996389511250348&mode=UPI&status=success&unmappedstatus=captured&key=DEMO_NO_PAYU_KEY&txnid=ASLD2611070250006408&amount=1500.00&productinfo=ASL Wallet top-up&firstname=Demo Retailer&email=demo.retailer@example.com&phone=9999999999&udf1=100001&udf2=&udf3=&udf4=&udf5=&bank_ref_num=431268907705&error=E000&error_Message=No Error&hash=917925627a65ffdfb639c3ec3f2cda534d7d49c3077e01af535b072b5f8bff7b1d0026bbe4af79a274814c4bc1ea01d05163c4bf16849431d1b1e0ec8faf1603
```

> **Server: callback checked** · Reverse hash OK, amount matches the order (₹1500.00).

> **Server: wallet credited (demo)** · Credited ₹1500.00 once for ASLD2611070250006408 and added a ledger entry. Demo only: your real UAT balance doesn't change.

> **Server: verify asked** · Confirms ASLD2611070250006408 with PayU (verify_payment) before any credit; the app's mihpayid is not trusted.

```http
POST /api/v1/payments/payu/verify
Authorization: Bearer <token>
Content-Type: application/json

{"txnid":"ASLD2611070250006408","mihpayid":"403996389511250348"}
```
```http
HTTP 200
Content-Type: application/json

{
  "status": true,
  "message": "Payment status fetched successfully",
  "data": {
    "txnid": "ASLD2611070250006408",
    "amount": "1500.00",
    "status": "SUCCESS",
    "isPaymentComplete": true,
    "walletCredited": true,
    "paymentMode": "UPI",
    "mihpayid": "403996389511250348",
    "bankRefNo": "431268907705",
    "reason": null,
    "balanceAfter": "2522.00",
    "completedAt": "2026-11-07T02:50:00+05:30"
  }
}
```

```http
GET /api/v1/payments/payu/ASLD2611070250006408/status
Authorization: Bearer <token>
```
```http
HTTP 200
Content-Type: application/json

{
  "status": true,
  "message": "Payment status fetched successfully",
  "data": {
    "txnid": "ASLD2611070250006408",
    "amount": "1500.00",
    "status": "SUCCESS",
    "isPaymentComplete": true,
    "walletCredited": true,
    "paymentMode": "UPI",
    "mihpayid": "403996389511250348",
    "bankRefNo": "431268907705",
    "reason": null,
    "balanceAfter": "2522.00",
    "completedAt": "2026-11-07T02:50:00+05:30"
  }
}
```

