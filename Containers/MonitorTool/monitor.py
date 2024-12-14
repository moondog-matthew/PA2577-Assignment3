import time
import pandas as pd
from pymongo import MongoClient
from datetime import datetime
import os

def get_collection_counts(db):
    collections = ['files', 'chunks', 'candidates', 'clones']
    return {coll: db[coll].count_documents({}) for coll in collections}

def get_status_updates(db, last_timestamp):
    query = {'timestamp': {'$gt': last_timestamp}} if last_timestamp else {}
    return list(db.statusUpdates.find(query).sort('timestamp', 1))

def main():
    # Connect to MongoDB
    client = MongoClient('mongodb://dbstorage:27017/')
    db = client.cloneDetector
    last_timestamp = None
    data = []
    
    while True:
        try:
            current_time = datetime.now().isoformat()
            counts = get_collection_counts(db)
            updates = get_status_updates(db, last_timestamp)
            if updates:
                last_timestamp = updates[-1]['timestamp']
            
            # Create data row
            row = {
                'timestamp': current_time,
                'files': counts['files'],
                'chunks': counts['chunks'],
                'candidates': counts['candidates'],
                'clones': counts['clones'],
                'latest_update': updates[-1]['message'] if updates else ''
            }
            
            # Store and fix data
            data.append(row)
            df = pd.DataFrame(data)
            df.to_csv('/data/monitor_output.csv', index=False)
            
            # Print status updates
            print(f"[{current_time}] Status - Files: {counts['files']}, "
                  f"Chunks: {counts['chunks']}, Candidates: {counts['candidates']}, "
                  f"Clones: {counts['clones']}")
            for update in updates:
                print(f"[{update['timestamp']}] {update['message']}")
            
            time.sleep(5)
            
        except Exception as e:
            print(f"Error: {e}")
            time.sleep(5)

if __name__ == "__main__":
    main()
