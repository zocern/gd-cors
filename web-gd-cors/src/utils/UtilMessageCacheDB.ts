/**
 * 消息缓存类 - 使用 IndexedDB 管理流式消息的本地缓存
 * 解决 localStorage 容量限制问题
 */

interface CacheData {
  content: string;
  lastChunkId: string | null;
  isWaitingResponse?: boolean;
}

const DB_NAME = "ChatMessageCache";
const DB_VERSION = 1;
const STORE_NAME = "sessionCache";

class MessageCacheDB {
  private db: IDBDatabase | null = null;
  private initPromise: Promise<void> | null = null;

  constructor() {
    this.initPromise = this.initDB();
  }

  /**
   * 初始化 IndexedDB
   */
  private async initDB(): Promise<void> {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION);

      request.onerror = () => {
        console.error("[MessageCacheDB] 打开数据库失败", request.error);
        reject(request.error);
      };

      request.onsuccess = () => {
        this.db = request.result;
        resolve();
      };

      request.onupgradeneeded = (event) => {
        const db = (event.target as IDBOpenDBRequest).result;

        // 创建对象存储空间，使用 sessionId 作为主键
        if (!db.objectStoreNames.contains(STORE_NAME))
          db.createObjectStore(STORE_NAME, { keyPath: "sessionId" });
      };
    });
  }

  /**
   * 确保数据库已初始化
   */
  private async ensureDB(): Promise<IDBDatabase> {
    if (this.db) return this.db;

    await this.initPromise;

    if (!this.db) throw new Error("数据库初始化失败");

    return this.db;
  }

  /**
   * 保存缓存
   */
  async save(
    sessionId: string,
    content: string,
    lastChunkId: string | null,
    isWaitingResponse: boolean = false,
  ): Promise<void> {
    if (!sessionId) return;

    try {
      const db = await this.ensureDB();
      const transaction = db.transaction([STORE_NAME], "readwrite");
      const store = transaction.objectStore(STORE_NAME);

      const data = {
        sessionId,
        content,
        lastChunkId,
        isWaitingResponse,
        timestamp: Date.now(), // 添加时间戳，便于后续清理过期数据
      };

      await new Promise<void>((resolve, reject) => {
        const request = store.put(data);
        request.onsuccess = () => resolve();
        request.onerror = () => reject(request.error);
      });
    } catch (e) {
      console.warn("[MessageCacheDB] 保存失败", e);
    }
  }

  /**
   * 读取缓存
   */
  async get(sessionId: string): Promise<CacheData | null> {
    if (!sessionId) return null;

    try {
      const db = await this.ensureDB();
      const transaction = db.transaction([STORE_NAME], "readonly");
      const store = transaction.objectStore(STORE_NAME);

      return new Promise((resolve, reject) => {
        const request = store.get(sessionId);

        request.onsuccess = () => {
          const result = request.result;
          if (!result) {
            resolve(null);
            return;
          }

          resolve({
            content: result.content,
            lastChunkId: result.lastChunkId,
            isWaitingResponse: result.isWaitingResponse,
          });
        };

        request.onerror = () => {
          console.warn("[MessageCacheDB] 读取失败", request.error);
          reject(request.error);
        };
      });
    } catch (e) {
      console.warn("[MessageCacheDB] 读取失败", e);
      return null;
    }
  }

  /**
   * 删除缓存
   */
  async remove(sessionId: string): Promise<void> {
    if (!sessionId) return;

    try {
      const db = await this.ensureDB();
      const transaction = db.transaction([STORE_NAME], "readwrite");
      const store = transaction.objectStore(STORE_NAME);

      await new Promise<void>((resolve, reject) => {
        const request = store.delete(sessionId);
        request.onsuccess = () => resolve();
        request.onerror = () => reject(request.error);
      });
    } catch (e) {
      console.warn("[MessageCacheDB] 删除失败", e);
    }
  }

  /**
   * 检查是否存在缓存
   */
  async has(sessionId: string): Promise<boolean> {
    const data = await this.get(sessionId);
    return data !== null;
  }

  /**
   * 清理所有缓存（可选功能）
   */
  async clear(): Promise<void> {
    try {
      const db = await this.ensureDB();
      const transaction = db.transaction([STORE_NAME], "readwrite");
      const store = transaction.objectStore(STORE_NAME);

      await new Promise<void>((resolve, reject) => {
        const request = store.clear();
        request.onsuccess = () => resolve();
        request.onerror = () => reject(request.error);
      });
    } catch (e) {
      console.warn("[MessageCacheDB] 清理失败", e);
    }
  }

  /**
   * 清理过期缓存（超过指定天数的缓存）
   */
  async clearExpired(days: number = 7): Promise<void> {
    try {
      const db = await this.ensureDB();
      const transaction = db.transaction([STORE_NAME], "readwrite");
      const store = transaction.objectStore(STORE_NAME);

      const expireTime = Date.now() - days * 24 * 60 * 60 * 1000;

      const request = store.openCursor();

      request.onsuccess = (event) => {
        const cursor = (event.target as IDBRequest).result;
        if (cursor) {
          const data = cursor.value;
          if (data.timestamp && data.timestamp < expireTime) {
            cursor.delete();
          }
          cursor.continue();
        }
      };
    } catch (e) {
      console.warn("[MessageCacheDB] 清理过期缓存失败", e);
    }
  }
}

export const messageCache = new MessageCacheDB();
export type { CacheData };
