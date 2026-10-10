# Create: Power Grid compatibility (VS / Forge 1.20.1)

## 対象と依存関係

- VS側の現在のForge 1.20.1ブランチのみ。Sable側は変更しない。
- Power Grid 0.6.2のForge版。GradleはModrinthリリースID `CBWW1upo` を固定する。
  同じ0.6.2でもNeoForge用のJarがあるため、バージョン名だけでは指定しない。
- 公開Jarの要求はCreate `[6.0.7,6.1.0)`、Architectury API `9.2.14+`。
  このプロジェクトではCreate 6.0.8-289 / Forge 47.4.13 / Java 17でビルドする。
- Power Gridは任意依存。配布JarへPower GridやArchitecturyを同梱しない。
- 参考ソース: https://github.com/patryk3211/PowerGrid/tree/architectury-1.20.1/dev
- 対象リリース: https://modrinth.com/mod/power-grid/version/CBWW1upo

## 実装

通常・マグネット電気コネクターにPower Gridの端子を1つずつ追加する。
端子の選択範囲はブロック全体、PGの銅線の取付位置は中心。
PowerGridCompatの任意依存ファクトリを、ブロック設置とBlockEntityTypeの両方で使用する。
未導入時は従来型のBEを生成し、PG専用クラスをロードしない。

共通BEをCreateのSmartBlockEntityへ移行し、PGのElectricBehaviourを標準の方法で初期化・同期する。
Forgeの既存Energy (IntTag) / TargetPos (CompoundTag)の保存形式を維持する。
FEの容量・全方向入出力・転送処理、CCの独立した有線ネットワークは変更しない。
両方の既存電気レンダラーとレンチ操作も維持する。

Sable側と同じく、GlobalElectricNetworks.makeSimpleConnectionでPG標準のElectricWireを1本作る。
両端は同じWireを共有し、反対側のtickで重複生成しない。
接続変更時は即座に検証し、安定時は20tickごとに再検証する。
解除時はWire.removeとscheduleIslandDiscoveryで回路を分割する。
片側の破壊・チャンク読み込み解除でも仮想線を取り除く。再読み込み時はTargetPosから再構築する。
PG自身のローカル銅線は、リンク解除だけでは破壊せず、ブロック破壊時はPG標準の処理で除去する。

VS側は船のストレージ座標で端子を特定する。同一Level内で、双方のTargetPosから
VSLinkUtil.findReciprocalLinkを通して実際の相手BEが相互に一致することを確認する。
Levelから直接見つからない船上BEは既存の登録済みBE検索にフォールバックする。
読み込み解除時は登録から外し、既存の仮想線も即座に除去する。
距離判定にはVSで変換したワールド座標を使用する。異なるLevel/Dimensionの回路は接続しない。

仮想線の抵抗は固定で0.015 ohm (PG銅線20m相当)。
電圧・電流・電圧降下・損失はPG標準ソルバーに任せ、FEには変換しない。
この仮想線の発熱・過電流破壊は今回の対象外。

## ビルド・自動検証

```text
gradlew.bat build reobfJar -PwithComputerCraft=true -PwithPowerGrid=true
```

PGの追加はコンパイル専用。withPowerGrid=trueは開発ランタイムにPG/Architecturyを追加するだけで、
配布Jarへの同梱や必須化はしない。VS自体は従来どおり別途導入する。

JUnitではMinecraftのLevel/PGのワールド管理をモック化し、端子・Wire・電気ソルバーは実物を使う。
重複防止、接続setterからの即時解除、再接続、破壊、アンロード、相互参照、
登録済み船上BEの検索、別Level拒否、PG不在のクラス読み込みを検証する。
ソルバーでは抵抗・電圧降下・損失・電圧反転・復路断線を検証する。
FEの旧NBT保存・同期・全方向Capabilityと既存CC/接続解除テストも実行する。
通常JUnitにはForgeのCapabilityToken transformerがないため、Capability生成だけをテスト内で代替する。
これらはゲーム起動・実際のVS動作の検証ではない。

## ゲーム内の確認項目 (ユーザー側で実施)

1. Power Gridなしで既存ワールドが読み込めることを確認する。検証用ワールドのコピーを使う。
2. Forge版PG 0.6.2とArchitectury API 9.2.14以降を導入する。
3. コネクター1組は導線1本なので、発電機・負荷間に往路と復路の計2組を用意する。
   機器とコネクターの間はPG銅線、コネクター同士は従来のレンチで接続する。
4. 通常型・マグネット型で電流と負荷動作を確認する。復路を切ると負荷が停止することを確認する。
5. 片側の手動解除・破壊、マグネットのレッドストーン解除、再接続・接続先変更を繰り返す。
6. 地上↔船、船↔別の船、船の移動・回転、最大距離超過、組立・分解を確認する。
7. 片側・両側のチャンク読み直し、保存・再起動後の復帰を確認する。
8. FE・CC・PGをそれぞれ単独および同時に接続し、既存経路も動作することを確認する。

## 制限

別Dimension接続、FE変換、抵抗設定、仮想線の熱破壊は対象外。
Power Gridの互換対象範囲は `[0.6.2,0.7.0)`。旧版は未検証。
実ゲームの起動、専用サーバー、VSの移動・組立・分解はここでは未検証。
Sable側の既存POWER_GRID.mdと、ユーザー変更済みの.gitignoreは上書きしない。
