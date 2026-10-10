# CC: Tweaked 有線ネットワーク対応 (NeoForge 1.21.1)

## 実装の範囲

- 電気ワイヤーコネクターとマグネット電気ワイヤーコネクターの既存リンクを、有線ネットワークの橋として使う。
- 全方向で CC の WiredElement capability を公開。隣接する CC ケーブル／有線モデムが通常の隣接接続を管理する。
- 双方の接続情報が互いの BlockEntity を指している時だけ、公開 API の WiredNode.connectTo で橋を作る。
- Peripheral の共有、追加／削除通知、モデムパケットは CC の標準ネットワークに任せる。独自の Lua API や Peripheral は追加しない。
- FE 転送、容量、レンダリング、レンチ操作、リンク保存形式は変更しない。CC 通信に FE / SU は不要。
- サーバー側だけでノードを作成。接続変更時に再評価し、距離やリンクの整合性も20 tickごとに確認する。
- 解除・破壊・アンロード時は橋と隣接エッジを外す。再ロード時はノードを作り直し、既存の接続情報から復旧する。
- 相手の未読み込みチャンクを強制ロードしない。片側アンロード中は通信できない。
- 接続判定と位置変換には既存の VSLinkUtil を使用し、サブレベルの識別情報も照合する。
- CC は任意依存。未導入の場合は capability を登録せず、CC API を使う実装を生成しない。

対象 API: CC: Tweaked 1.120.2 / Minecraft 1.21.1 / NeoForge。
Maven artifact は forge という名前だが、このバージョンの対象ローダーは NeoForge。
内部実装へのアクセスや Mixin は使用しない。

公開API: https://tweaked.cc/mc-1.21.x/javadoc/dan200/computercraft/api/network/wired/WiredElement.html

## ビルドと自動テスト

```text
./gradlew.bat test build --no-daemon
./gradlew.bat test build -PwithComputerCraft=true --no-daemon
```

通常ビルドでは CC 本体を実行時依存に追加しない。
withComputerCraft=true の時だけ開発実行環境に CC 本体を追加する。
API は compileOnly。配布 JAR に CC を同梱しない。

CC テストは公開 API で実ノードを作り、Peripheral の追加／削除通知を観測する。
リンク解除、ノード削除、削除の二重実行、相手の再生成、別経路があるループの切断を検証する。
標準パケットの双方向通信と解除後の停止も検証する。
これはゲーム内のブロック配置、チャンク遷移、Lua 実行のテストではない。

## ゲーム内の確認手順

1. CC なしで起動し、既存の FE 転送とレンチ操作が従来どおり動くことを確認する。
2. CC 1.120.2 を導入し、Computer → 有線モデム／ケーブル → コネクターA → 既存リンク → コネクターB → ケーブル／有線モデム → チェストを配置する。
3. チェスト側の有線モデムを右クリックして Peripheral 共有を有効にする。ケーブルのない Computer 単体との接触は接続対象ではない。
4. 通常電気コネクター同士、マグネット同士でそれぞれ確認する。ケーブルを6方向から接続して確認する。
5. コンピューターで lua を起動し、次を実行する。inventory名には実際に返ったチェストの名前を使う。

```lua
print(textutils.serialize(peripheral.getNames()))
local inv = peripheral.wrap("minecraft:chest_0")
print(inv.size())
print(textutils.serialize(inv.list()))
print(textutils.serialize(peripheral.call("minecraft:chest_0", "list")))
```

6. 有線モデム同士で同じチャネルを開き、modem.transmit と modem_message の受信を両方向で確認する。
7. peripheral / peripheral_detach イベントを待つプログラムを起動し、共有の切替、ケーブル破壊、レンチでリンク解除、コネクター破壊で適切に変化することを確認する。
8. 相手を破壊／再設置して再リンクし、古い Peripheral が残らず、新しい接続で復旧することを確認する。
9. 片側をアンロードして通信が停止し、再ロードとワールド再起動で復旧することを確認する。チャンク強制ロードは使わない。
10. 複数ペアをケーブルでつないだ経路とループを作り、1本の橋だけ解除しても別経路が残る場合は通信でき、最後の経路を外すと通信できなくなることを確認する。
11. 地上↔Sable sub-level、sub-level↔sub-level、移動・回転中でも確認する。VS 環境も利用できる場合は同様に確認する。
12. CC 通信中にも FE 転送が動き、FE が空でも CC 通信できることを確認する。専用サーバーでも同じ配置で確認する。

ゲーム内動作は利用者側で確認する。自動テスト成功は船／サブレベル内での実動作やチャンク復旧の保証ではない。
