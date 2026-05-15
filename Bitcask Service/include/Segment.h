#pragma once
#include <bits/stdc++.h>
using namespace std;
#define ll long long
struct Record {
    ll keySize;
    ll valueSize;
    ll timestamp;
    string value;
    string key;
};

struct KeyDir {
    ll file_id;
    ll offset;
    ll valueSize;
    ll timestamp;
};

class Segment {
    private:
    ll file_id;
    string path;
    fstream file;

    public:
    Segment (string &path,ll file_id);

    ~Segment();
    ll write (string&key,string &value,ll timestamp);
    string read(ll offset,ll valueSize);
    void iterate();
    ll size();
};