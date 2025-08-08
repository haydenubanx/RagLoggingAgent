# RAG Logging Agent

This project is an MCP Agent built using Java and Spring Boot, designed to provide tools for interaction between the agent and the qdrant logs. It leverages the capabilities of Qdrant for vector search and metadata-based search, allowing users to query and analyze web server logs efficiently.

## Dataset

The dataset used for this project consists of web server log files in the Apache/Nginx format. These logs are parsed, embedded, and stored in a Qdrant collection for fast vector and metadata-based search. The public domain data set I used can be found here: [Log Dataset](https://www.kaggle.com/datasets/vishnu0399/server-logs?resource=download).
For this project the Qdrant vector database is stored using HNSW (Hierarchical Navigable Small World) algorithm, which is suitable for high-dimensional vector data and provides efficient nearest neighbor search.

## Requirements

To run this project, you will need:

- **A Qdrant database**: Set up and accessible (see [Qdrant documentation](https://qdrant.tech/documentation/)).
- **Claude code or another MCP client**: To interact with the agent's tools.
- **API keys**:
    - **Anthropic API key** (for Claude)
    - **OpenAI API key** (if using OpenAI models)
- **Environment variables**:
    - `ANTHROPIC_API_KEY` — your Anthropic Claude API key
    - `OPENAI_API_KEY` — your OpenAI API key
    - `QDRANT_COLLECTION_NAME` — the name of the Qdrant collection to use
    - `QDRANT_URL` — the URL of your Qdrant instance

## Setup

1. **Clone the repository** and install dependencies using Maven.
2. **Set the required environment variables** (see above).
3. **Ensure your Qdrant database is running** and the collection exists (the app can create it if needed).
4. **Run the agent**:
   ```sh
   mvn clean package
   ```

## MCP Integration

To use the agent with Claude or another MCP client:

1. Ensure your MCP client is configured with the correct tool names (matching the regex `^[a-zA-Z0-9_-]{1,64}$`).
2. Connect to the running agent and invoke the available tools, such as:
    - `Qdrant_Vector_Similarity_Search`
    - `Qdrant_Get_All_Points`
    - `Qdrant_Metadata_Filtered_Search`
    - `Qdrant_Count_Logs_by_Filter`
    - `Qdrant_Aggregate_Logs`
    - `Qdrant_Get_Distinct_Metadata_Values`
    - `Qdrant_Visualize_Log_Metadata`

## Notes

- The application caches all points in memory on startup for fast access by Claude/MCP.
- For more details, see the code in `src/main/java/org/hayden/ragloggingagent/clients/QdrantClient.java`.

---


## My website: [haydeneubanks.co.uk](https://haydeneubanks.co.uk)
## LinkedIn: [HaydenEubanks](https://www.linkedin.com/in/hayden-eubanks-794265280)
