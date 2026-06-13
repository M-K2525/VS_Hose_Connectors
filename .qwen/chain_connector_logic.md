# チェーンコネクターの描画・回転方向ロジックメモ

作成日: 2026-06-14

対象:

- `src/main/java/com/mk2525/vsfluidlink/content/ChainConnector/ChainConnectorRenderer.java`
- `src/main/java/com/mk2525/vsfluidlink/content/MagnetChainConnector/MagnetChainConnectorRenderer.java`
- `src/main/java/com/mk2525/vsfluidlink/content/ChainConnector/ChainConnectorBlockEntity.java`
- `src/main/java/com/mk2525/vsfluidlink/content/MagnetChainConnector/MagnetChainConnectorBlockEntity.java`

## 目的

チェーンコネクターとマグネットチェーンコネクターは、2本のチェーンで2つのシャフト間の回転を伝える見た目にしている。

このとき必要な条件は次の通り。

- チェーンの始点・終点は、ブロック面上ではなく、ブロック中心からシャフト周りにずれた2点にする。
- 横方向、上下方向、斜め方向のどの接続でも、2本のチェーンが急に横並びへ反転したり、端点がずれたりしないようにする。
- VS / Sable の座標変換後でも、始点側と終点側で同じワールド空間上の考え方になるようにする。
- 見た目のチェーン移動方向と、Create のシャフト回転方向が一致するようにする。
- 真上同士のような特殊ケースでは、チェーンがクロスしない端点ペアを選ぶ。

## 描画側の始点・終点ロジック

描画側では、まず接続先との差分をワールド空間で求める。

```java
Vec3 startPos = VSLinkUtil.Client.getRenderWorldPos(level, selfPos);
Vec3 endPos = VSLinkUtil.Client.getRenderWorldPos(level, targetPos);
Vec3 diff = endPos.subtract(startPos);
```

その後、始点ブロックのローカル空間へ変換する。

```java
Vec3 localDiff = VSLinkUtil.Client.renderWorldVectorToLocal(level, selfPos, diff);
```

始点側アンカーは `getLocalAnchorOffsets(startState, localDiff)` で求める。

終点側アンカーは、終点ブロック自身のローカル空間で一度求めたあと、ワールド方向ベクトルに変換し、さらに始点ブロックのローカル空間へ変換している。

```java
Vec3[] targetOffsetsWorld = getWorldAnchorOffsets(level, targetPos, endState, diff.scale(-1));
endAnchors[i] = VSLinkUtil.Client.renderWorldVectorToLocal(level, selfPos, targetOffsetsWorld[i]).toVector3f();
```

これにより、VS / Sable のように始点と終点が別の座標系にいても、最終的には始点側の描画ローカル空間だけでチェーンを描画できる。

## アンカー方向の決定

各コネクターのアンカー方向は `getAnchorVector(state, connection)` で決める。

基本は次の式。

```java
anchor = shaft x forward
```

- `shaft`: コネクターのシャフト軸。
- `forward`: 接続先へ向かう方向。
- `anchor`: シャフト周りで2本のチェーンを上下に分けるための方向。

この外積により、「接続方向に対して」ではなく「シャフトの回転面に対して」チェーンの2点が決まる。

ただし `shaft` と `forward` がほぼ平行な場合、外積がほぼゼロになる。このときは `getPreferredAnchorVector` を使う。

`getPreferredAnchorVector` は、ブロックの見た目の side / up からシャフト軸成分を取り除いた方向を使う。これにより、真上接続や真横接続で不安定に反転しにくくしている。

また、外積で得た `anchor` は `preferred` と同じ半球を向くように符号をそろえる。

```java
if (anchor.dot(preferred) < 0) {
    anchor = anchor.scale(-1);
}
```

最終的な2本のチェーンのローカルアンカーは次の2点。

```java
+anchor * ANCHOR_OFFSET
-anchor * ANCHOR_OFFSET
```

現在の `ANCHOR_OFFSET` は `3.0f / 16.0f`。

## 終点ペアの入れ替え

始点側と終点側で、それぞれ `+anchor` / `-anchor` を作るだけだと、真上同士のようなケースで次のようにクロスすることがある。

- 始点 `+anchor` -> 終点 `+anchor`
- 始点 `-anchor` -> 終点 `-anchor`

しかしワールド空間上では、終点側の `+anchor` が始点側の `-anchor` と同じ側に来る場合がある。

そのため描画直前に、次の2通りの合計距離を比較する。

- direct:
  - 始点0 -> 終点0
  - 始点1 -> 終点1
- swapped:
  - 始点0 -> 終点1
  - 始点1 -> 終点0

`swapped` の方が短い場合、終点アンカーを入れ替える。

```java
if (swapped + 1.0e-6f < direct) {
    swap(endAnchors[0], endAnchors[1]);
}
```

この処理は `alignEndAnchors` に入っている。

これにより、真上同士でチェーンがクロスする問題を避ける。

## チェーンアニメーション方向

描画上のチェーン移動方向は、始点側のシャフト軸・アンカー・接続方向から決める。

```java
movement = (shaft x anchor) . forward
```

`movement` が正か負かで、テクスチャの `vOffset` の符号を決める。

現在は見た目上の移動方向を合わせるため、次のように反転している。

```java
return movement > 0 ? -1.0f : 1.0f;
```

2本目のチェーンは1本目と逆向きに動く必要があるため、描画時に `textureOffset` と `-textureOffset` を使う。

## 回転方向ロジック

Create の回転伝播は `propagateRotationTo` から `getChainRotationModifier` に入り、始点側と終点側の接線方向が一致するかどうかで modifier を決める。

まず、始点と終点のワールド位置差分を取る。

```java
Vec3 worldConnection = toWorld.subtract(fromWorld);
```

それぞれのブロックのローカル空間へ変換する。

```java
Vec3 fromConnection = VSLinkUtil.worldVectorToLocal(level, worldPosition, worldConnection);
Vec3 toConnection = VSLinkUtil.worldVectorToLocal(level, targetPos, worldConnection.scale(-1));
```

各側で描画と同じ `getAnchorVector` を使ってアンカー方向を求める。

```java
Vec3 fromAnchor = getAnchorVector(stateFrom, fromConnection);
Vec3 toAnchor = getAnchorVector(stateTo, toConnection);
```

接線方向の符号は、描画のチェーン移動方向と同じ考え方で計算する。

```java
sign = (shaft x anchor).normalize方向 . connection.normalize方向
```

実装上は `getTangentialMovementSign`。

始点側と終点側の符号が同じなら、チェーンでつながったシャフトとしては反転が必要。
符号が逆なら、反転不要。

```java
return fromSign * toSign >= 0 ? -1.0f : 1.0f;
```

## 非クロス端点ペアと回転方向の同期

描画側で終点ペアを入れ替えると、見た目のチェーンは非クロスになる。

しかし回転伝播側が入れ替え前の端点対応のままだと、真上同士などで片方のシャフト回転とチェーン移動方向が合わなくなる。

そのため、BlockEntity 側でも `usesSwappedAnchorPair` で同じ判定を行う。

処理内容は描画側の `alignEndAnchors` と同じ考え方で、ワールド空間上のアンカーを使って direct / swapped の合計距離を比べる。

```java
Vec3 fromAnchorWorld = VSLinkUtil.localVectorToWorld(level, worldPosition, fromAnchorLocal);
Vec3 toAnchorWorld = VSLinkUtil.localVectorToWorld(level, targetPos, toAnchorLocal);
```

`swapped` の方が短い場合、終点側の接線符号を反転する。

```java
if (usesSwappedAnchorPair(...)) {
    toSign = -toSign;
}
```

これにより、描画上の端点対応と、Create に渡す回転方向の前提が一致する。

## 注意点

- 描画側だけを変更すると、見た目は直っても回転方向と合わなくなる可能性がある。
- 回転伝播側だけを変更すると、シャフトは合ってもチェーンアニメーションやクロス状態と矛盾する可能性がある。
- `getAnchorVector`、`alignEndAnchors`、`usesSwappedAnchorPair` は実質的に同じ幾何ロジックを共有している。片方だけを変えると再発しやすい。
- VS / Sable 対応のため、終点側のアンカーや接続方向は必ず適切なローカル/ワールド変換を通す必要がある。
- 真上・真下など `shaft` と `connection` が平行に近いケースは外積が不安定になるため、`preferred` fallback が重要。

