#include "../include/Segment.h"

Segment::Segment(string &path, long long file_id) {
    this->path = path;
    this->file_id = file_id;
    file.open(path, ios::in | ios::binary | ios::app | ios::out);
    //in for reading, out for writing, binary to treat the file as a raw bytes,app for append
    if (!file.is_open()) {
        throw runtime_error("File open error");
    }
}

Segment::~Segment() {
    if (file.is_open()) {
        file.close();
    }
}

ll Segment::write(string &key, string &value, ll timestamp) {
    file.seekp(0,ios::end);//move the pointer to the end of the file by 0 bytes
    ll offset = file.tellp();
    ll keySize =key.size();
    ll valueSize =value.size();
    file.write((char*)&(keySize),sizeof(ll));
    file.write((char*)&(valueSize),sizeof(ll));
    file.write((char *)&timestamp,sizeof(ll));
    file.write(value.data(),valueSize);
    file.write(key.data(),keySize);
    file.flush();
    return offset;
}

string Segment::read(long long offset, long long valueSize) {
    ll val_offset=offset+3*sizeof(ll);
    file.seekg(val_offset,ios::beg);
    string value(valueSize,'\0');
    file.read(value.data(),valueSize);
    return value;
}

vector<pair<Record, long long> > Segment::iterate() {
    vector<pair<Record, long long> > result;
    file.seekg(0,ios::beg);
    while(!file.eof()) {
        ll offset=file.tellg();
        Record record;
        file.read((char*)&record.keySize,sizeof(ll));
        file.read((char*)&record.valueSize,sizeof(ll));
        file.read((char*)&record.timestamp,sizeof(ll));
        record.value.resize(record.valueSize);
        file.read(record.value.data(),record.valueSize);
        record.key.resize(record.keySize);
        file.read(record.key.data(),record.keySize);
        if (file.fail())
            break;
        result.push_back({record,offset});

    }
    file.clear();
    return result;
}
ll Segment::size() {

    file.seekg(0, ios::end);

    return file.tellg();
}
