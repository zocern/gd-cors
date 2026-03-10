package com.cors.config.milvus;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.database.request.CreateDatabaseReq;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Configuration
public class MilvusClientConfig {

    @Value("${milvus.host}")
    private String host;
    @Value("${milvus.port}")
    private Integer port;
    @Value("${milvus.token}")
    private String token;
    @Value("${milvus.database}")
    private String databaseName;
    @Value("${milvus.collection}")
    private String collectionName;
    @Value("${milvus.dimension}")
    private Integer dimension;

    @Bean
    public MilvusClientV2 milvusClient() throws InterruptedException {
        ConnectConfig connectConfig = ConnectConfig.builder()
                .uri("http://" + host + ":" + port)
                .token(token)
                .build();
        MilvusClientV2 client = new MilvusClientV2(connectConfig);

        // 查询已有数据库列表
        List<String> existingDatabases = client.listDatabases().getDatabaseNames();

        // 判断目标数据库是否存在，不存在则创建
        if (!existingDatabases.contains(databaseName)) {
            client.createDatabase(CreateDatabaseReq.builder()
                    .databaseName(databaseName)
                    .build());
        }

        client.useDatabase(databaseName);

        if (!client.hasCollection(HasCollectionReq.builder().collectionName(collectionName).build())) {

            // 定义 Schema
            CreateCollectionReq.CollectionSchema schema = MilvusClientV2.CreateSchema();
            schema.addField(AddFieldReq.builder()
                    .fieldName("id")
                    .dataType(DataType.VarChar)
                    .isPrimaryKey(true)
                    .autoID(false) // LangChain4j 会在客户端生成 UUID 作为 ID
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("text")
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("metadata")
                    .dataType(DataType.JSON)
                    .maxLength(65535)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("vector")
                    .dataType(DataType.FloatVector)
                    .dimension(dimension)
                    .build());

            // 定义索引
            IndexParam indexParamForVectorField = IndexParam.builder()
                    .fieldName("vector")
                    .indexType(IndexParam.IndexType.HNSW)
                    .extraParams(Map.of(
                            "M", 64,             // 每个节点的邻居数量
                            "efConstruction", 256        // 索引构建时的搜索范围
                    ))
                    .metricType(IndexParam.MetricType.IP) // 指定使用内积 (IP) 计算相似性。由于数据已做 L2 归一化，IP 运算速度最快且结果等效于余弦相似度
                    .build();

            // 针对 metadata 字段构建倒排索引
            Map<String, Object> extraParams = new HashMap<>();
            extraParams.put("json_path", "metadata[\"storage_key\"]");
            extraParams.put("json_cast_type", "varchar");
            IndexParam indexParamForMetadataStorageKeyField = IndexParam.builder()
                    .fieldName("metadata")
                    .indexName("storage_key_index")
                    .indexType(IndexParam.IndexType.INVERTED)
                    .extraParams(extraParams)
                    .build();

            List<IndexParam> indexParams = new ArrayList<>();
            indexParams.add(indexParamForVectorField);
            indexParams.add(indexParamForMetadataStorageKeyField);

            // Create a collection with schema and index parameters
            CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                    .collectionName(collectionName)
                    .collectionSchema(schema)
                    .numShards(4) // With shard number
                    .indexParams(indexParams)
                    .build();
            client.createCollection(createCollectionReq);

            client.loadCollection(LoadCollectionReq.builder()
                    .collectionName(collectionName)
                    .build());

            // Get load state of the collection
            GetLoadStateReq getLoadStateReq = GetLoadStateReq.builder()
                    .collectionName(collectionName)
                    .build();
            Boolean loaded = client.getLoadState(getLoadStateReq);
            System.out.println(loaded);
        }

        // 删除 Collection & Database
//        DropCollectionReq dropQuickSetupParam = DropCollectionReq.builder()
//                .collectionName(collectionName)
//                .build();
//        client.dropCollection(dropQuickSetupParam);
//        client.dropDatabase(DropDatabaseReq.builder()
//                .databaseName(databaseName)
//                .build());

        return client;
    }
}

