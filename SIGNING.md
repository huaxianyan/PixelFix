# 发布签名

正式包必须使用与 [`signing/release-certificate.pem`](signing/release-certificate.pem) 匹配的私钥签名。

| 项目 | 值 |
| --- | --- |
| 证书主题 | `CN=Pixel Fix, OU=Release, O=huaxianyan, C=CN` |
| 证书 SHA-256 | `48cda999ed4dcc8b86c297c949f319b977cfa98ffd3c1bce70bad95ba72cc876` |
| 有效期至 | 2126-10-03 |
| 密钥库 | `signing/pixelfix-release.p12`（PKCS#12，RSA 4096） |
| 别名 | `pixelfix` |

计算仓库内基准证书的 SHA-256：

```bash
openssl x509 -in signing/release-certificate.pem -outform DER | sha256sum
```

## 本地材料

密钥库与口令**不进 Git**（`.gitignore` 已排除）：

- `signing/pixelfix-release.p12`
- `signing/signing-credentials.txt`（含 `storePassword`、`keyPassword`）

构建时 `app/build.gradle.kts` 会读取 `signing/signing-credentials.txt`；文件缺失时 release 变体不签名，不会静默改用调试签名。

## 备份

当前材料只在本机 `E:/Dev/PixelFix/signing/`。NAS 恢复后需手工复制到
`\192.168.7.216\homes\NeKo7inA\dev\Android Signing\PixelFix\`，至少包含密钥库与凭据文件。

私钥丢失意味着已安装用户无法覆盖升级，只能卸载重装。只有公开证书无法签发新包。
