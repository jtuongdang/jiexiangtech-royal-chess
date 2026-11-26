# ChessMaster online beta

Use **Play online** in the desktop navigation. The organizer creates a room and shares its
12-character invite code and server URL. The guest joins the same room, then either seat
can resume from the same profile and device. ChessMaster stores one resumable room per local
profile. The server retains active and finished rooms for up to 30 days after the last state
change. An unclaimed invitation expires after 20 minutes.

The closed beta uses an organizer key and independent expiring room seats. It uses the existing
chess domain for legality, mate, timeouts and draws. Client-side settings and offline games still
use SQLite. Cloud accounts, rated matchmaking, online game-history search and hosted analysis
remain future work.

## Run a local room

From the repository root, with Docker running:

```sh
export CHESS_BETA_KEY="$(openssl rand -hex 24)"
docker compose up -d --build
curl --fail http://localhost:8080/health
mvn clean verify
scripts/run.sh
```

Enter `http://localhost:8080` in **Play online**. The organizer key is the value generated in
the same shell. For a separate guest on the same machine, open another desktop instance with
a separate data directory:

```sh
CHESSMASTER_DATA_DIR="$(mktemp -d)" scripts/run.sh
```

The local MongoDB is isolated on the Compose network with a named volume. To stop this stack
and keep its data, use `docker compose down`. Use a distinct key for a hosted beta.

## Deploy the private image

The verified repository is [jtuongdev/jiexiangtech-royal-chess-server](https://hub.docker.com/r/jtuongdev/jiexiangtech-royal-chess-server).
The image runs a Java 21 runtime on `linux/amd64`. The bundled desktop app uses Java 17.

1. In Atlas, create a **Free** cluster for the beta and a database user with read/write
   privileges for the selected database. Use Singapore if available for the desired tier.
   Put the database name in the Atlas URI, such as `royal_beta`, so the service selects it.
2. In Render, create a **Free Web Service → Existing Image** in the corresponding region.
   Enter the tested private Docker Hub reference and use a Docker Hub token with read access.
   Configure `/health` as the health-check path.
3. Set `MONGODB_URI` and `CHESS_BETA_KEY` directly in Render's environment settings.
   Add the service's outbound CIDR ranges to the Atlas network access list. The service
   starts only with a valid database configuration and an organizer key of at least 24 characters.
4. Check `https://<service>.onrender.com/health`. It should return `status: ok` and the
   intended build revision. Test a complete match from two desktop instances using the HTTPS URL.
5. Optional automated deploys use the **Publish online server** GitHub Actions workflow.
   Configure the repository variables `DOCKERHUB_IMAGE`, `DOCKERHUB_USERNAME`,
   and `RENDER_SERVICE_URL`. Add `DOCKERHUB_TOKEN` with permission to push the image,
   and put the Render service's deploy hook in the `RENDER_DEPLOY_HOOK` secret.
   Run the workflow with **deploy** selected to publish and verify the exact image digest.
   The same workflow can publish an image without deploying it.

Store access tokens and connection strings in the relevant service's secret settings. Use
the desktop login only for local profiles and the beta organizer key only for starting rooms.

[Render image deployment](https://render.com/docs/deploying-an-image) ·
[Render and MongoDB Atlas connectivity](https://render.com/docs/connect-to-mongodb-atlas) ·
[Render free service limits](https://render.com/docs/free)

## Consistency and testing

Each room document contains the complete move history, position, server deadline, result
and a monotonic state revision. A version-checked MongoDB update commits a command once.
Each player's access token is stored on the server as a SHA-256 hash. HTTP commands and
snapshots are authoritative. The WebSocket sends revision notifications to the two seats.
The desktop also resynchronizes periodically to recover an interrupted stream. A timeout
becomes final on the first state request or command after the persisted deadline.

The live MongoDB integration test creates a room, joins a second seat, plays moves,
receives WebSocket notifications, reconnects and restarts the HTTP server. It verifies that
the stored position survives and that retransmitting an already accepted command adds no move.
To include it in the local Maven reactor, set `ROYAL_TEST_MONGO_URI` to a test database
before running `mvn clean verify`. The CI integration job provides its own disposable MongoDB.
