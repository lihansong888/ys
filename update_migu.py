import requests

def main():
    output = []
    
    # 1. 精品频道 (保留你实测有效的 adultiptv 源)
    output.append("精品频道,#genre#")
    try:
        r = requests.get("http://adultiptv.net/chs.m3u", timeout=10)
        if r.status_code == 200:
            name = ""
            for line in r.text.split('\n'):
                if "#EXTINF" in line: name = line.split(',')[-1].strip()
                elif "http" in line and name:
                    output.append(f"{name},{line.strip()}")
                    name = ""
    except: pass

    # 2. 咪咕官方 (基于你抓包发现的 mgsp-ali2 阿里云节点)
    output.append("咪咕官方,#genre#")
    
    # 根据你提供的 CCTV-3 路径逻辑，批量生成核心频道
    # 路径：/wd_r2/ocn/[频道标识]/3000/01.m3u8
    base_ali = "http://mgsp-ali2.live.miguvideo.com:8088/wd_r2/ocn"
    migu_list = [
        ("CCTV-1 高清", f"{base_ali}/cctv1hd/3000/01.m3u8"),
        ("CCTV-3 高清", f"{base_ali}/cctv3hd/3000/01.m3u8"),
        ("CCTV-5 高清", f"{base_ali}/cctv5hd/3000/01.m3u8"),
        ("CCTV-6 高清", f"{base_ali}/cctv6hd/3000/01.m3u8"),
        ("CCTV-8 高清", f"{base_ali}/cctv8hd/3000/01.m3u8")
    ]
    
    for name, url in migu_list:
        output.append(f"{name},{url}")

    # 3. 核心直播 (合并你之前认可的 yimi321 IPTV 源)
    output.append("核心直播,#genre#")
    try:
        res = requests.get("https://gitee.com/yimi321/tv/raw/master/tv.png", timeout=10)
        if res.status_code == 200:
            for line in res.text.split('\n'):
                if "," in line and "http" in line:
                    output.append(line.strip())
    except: pass

    # 4. 写入 migu.txt
    with open("migu.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(output))
    print("已应用你抓包获取的阿里 CDN 节点")

if __name__ == "__main__":
    main()
